package com.writingassistant.server;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone web entry point. The desktop main class and database are intentionally untouched. */
public final class OriginalityDeskServer implements AutoCloseable {
    private static final String COOKIE = "desk_session";
    private static final Set<String> STATIC = Set.of("/", "/index.html", "/app.js", "/styles.css", "/favicon.svg", "/favicon.ico");
    private final ServerConfig config;
    private final ServerDatabase database;
    private final Service service;
    private final JobWorker worker;
    private final HttpServer http;
    private final ThreadPoolExecutor requests;
    private final Security.Limiter limiter = new Security.Limiter();
        private final Semaphore pdfExports = new Semaphore(2);
    private final String origin;
    private final String authority;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean started;

    public OriginalityDeskServer(ServerConfig config) throws Exception {
        this.config = config;
        // JDK HttpServer has no per-instance header/body timeouts; set conservative JVM defaults.
        defaultProperty("sun.net.httpserver.maxReqTime", "20");
        defaultProperty("sun.net.httpserver.maxRspTime", "30");
        defaultProperty("sun.net.httpserver.idleInterval", "30");
        defaultProperty("sun.net.httpserver.maxConnections", "64");
        defaultProperty("sun.net.httpserver.maxReqHeaders", "64");
        defaultProperty("sun.net.httpserver.maxReqHeaderSize", "16384");
        database = new ServerDatabase(config.dataDir());
        HttpServer bound = null;
        JobWorker jobs = null;
        ThreadPoolExecutor pool = null;
        try {
            service = new Service(database);
            jobs = new JobWorker(service, config);
            worker = jobs;
            bound = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 32);
            http = bound;
            URI supplied = URI.create(config.publicOrigin());
            // Port zero is supported for embedded integration tests, never advertised to clients.
            origin = config.port() == 0 && supplied.getPort() == 0
                    ? new URI(supplied.getScheme(), null, supplied.getHost(), http.getAddress().getPort(), null, null, null).toString()
                    : config.publicOrigin();
            authority = URI.create(origin).getRawAuthority();
            pool = new ThreadPoolExecutor(8, 8, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "desk-http"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
            requests = pool;
            http.setExecutor(requests);
            http.createContext("/", this::handle);
        } catch (Exception e) {
            if (bound != null) bound.stop(0);
            if (pool != null) pool.shutdownNow();
            if (jobs != null) jobs.close();
            database.close(); throw e;
        }
    }
    private static void defaultProperty(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
    public synchronized void start() {
        if (started || closed.get()) throw new IllegalStateException("Server cannot be started twice");
        started = true; worker.start(); http.start();
    }
    public int port() { return http.getAddress().getPort(); }
    public String publicOrigin() { return origin; }

    public static void main(String[] args) throws Exception {
        OriginalityDeskServer server = new OriginalityDeskServer(ServerConfig.fromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception e) { System.err.println("Server shutdown did not complete cleanly"); }
        }, "desk-shutdown"));
        server.start();
        System.out.println("Originality Desk server listening at " + server.publicOrigin());
    }

    private void handle(HttpExchange exchange) throws IOException {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        try {
            headers(exchange);
            exchange.getResponseHeaders().set("X-Request-Id", requestId);
            if (closed.get()) throw new ApiException(503, "UNAVAILABLE", "Server is shutting down");
            String host = oneHeader(exchange, "Host");
            if (host == null || !authority.equalsIgnoreCase(host)) throw new ApiException(403, "INVALID_HOST", "Use the configured public origin");
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath == null || !rawPath.matches("/[a-zA-Z0-9/_.-]*") || rawPath.contains("..") || rawPath.contains("//")) throw ApiException.missing();
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            limiter.take("all:" + ip, 600);
            String method = exchange.getRequestMethod();
            if (rawPath.equals("/health/live") || rawPath.equals("/health/ready")) {
                requireMethod(method, "GET");
                boolean ready = rawPath.equals("/health/live") || worker.alive() && service.ready();
                json(exchange, ready ? 200 : 503, Map.of("status", ready ? "UP" : "DOWN")); return;
            }
            if (!rawPath.startsWith("/api/v1/")) {
                requireMethod(method, "GET"); serveStatic(exchange, rawPath); return;
            }
            sameOrigin(exchange, !method.equals("GET"));
            if (rawPath.equals("/api/v1/auth/register") || rawPath.equals("/api/v1/auth/login")) {
                requireMethod(method, "POST"); limiter.take("auth:" + ip, 15);
                JsonObject input = body(exchange, false);
                Service.Authentication auth = rawPath.endsWith("/register") ? service.register(input) : service.login(input);
                setCookie(exchange, auth.token(), false);
                json(exchange, rawPath.endsWith("/register") ? 201 : 200, auth.body()); return;
            }
            String token = cookie(exchange);
            Service.Actor actor = service.authenticate(token);
            limiter.take("user:" + actor.userId(), 360);
            Map<String,String> query = query(exchange.getRequestURI().getRawQuery());
            String[] path = rawPath.substring("/api/v1/".length()).split("/", -1);
            if (rawPath.equals("/api/v1/auth/me")) {
                requireMethod(method, "GET"); json(exchange, 200, service.me(actor));
            } else if (rawPath.equals("/api/v1/auth/logout")) {
                requireMethod(method, "POST"); body(exchange, true); service.logout(actor, token);
                setCookie(exchange, "", true); empty(exchange, 204);
            } else if (rawPath.equals("/api/v1/dashboard")) {
                requireMethod(method, "GET"); json(exchange, 200, service.dashboard(actor));
            } else if (path[0].equals("documents")) {
                documents(exchange, method, path, query, actor);
            } else if (path[0].equals("analyses")) {
                analyses(exchange, method, path, query, actor);
            } else if (path[0].equals("reports")) {
                requireMethod(method, "GET"); reports(exchange, path, actor);
            } else if (path[0].equals("admin") && path.length == 2) {
                requireMethod(method, "GET"); actor.admin();
                if (path[1].equals("health")) json(exchange, 200, service.adminHealth(actor, worker.alive()));
                else if (Set.of("jobs", "audit-logs").contains(path[1]))
                    json(exchange, 200, service.adminList(actor, path[1], page(query), size(query)));
                else throw ApiException.missing();
            } else throw ApiException.missing();
        } catch (ApiException e) {
            if (e.status == 429) exchange.getResponseHeaders().set("Retry-After", "60");
            if (exchange.getResponseCode() == -1) json(exchange, e.status, Map.of("error", Map.of("code", e.code, "message", e.getMessage())));
        } catch (Exception e) {
            System.err.println("desk-http: request failed (" + e.getClass().getSimpleName() + ")");
            if (exchange.getResponseCode() == -1)
                json(exchange, 500, Map.of("error", Map.of("code", "INTERNAL_ERROR", "message", "Request could not be completed")));
        } finally {
            try { exchange.close(); }
            finally {
                System.out.println(RequestEvents.completed(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                        exchange.getResponseCode(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), requestId));
            }
        }
    }
    private void documents(HttpExchange x, String method, String[] path, Map<String,String> query, Service.Actor actor) throws Exception {
        if (path.length == 1) {
            if (method.equals("GET")) json(x, 200, service.documents(actor, query.get("kind"), page(query), size(query)));
            else if (method.equals("POST")) {
                limiter.take("upload:" + actor.userId(), 30);
                byte[] bytes = readBounded(x, Service.MAX_UPLOAD);
                json(x, 201, service.upload(actor, query.get("filename"), query.getOrDefault("kind", "SUBMISSION"), oneHeader(x, "Content-Type"), bytes));
            } else throw methodError();
        } else if (path.length == 2 && !path[1].isEmpty()) {
            if (method.equals("GET")) json(x, 200, service.document(actor, path[1]));
            else if (method.equals("DELETE")) { service.deleteDocument(actor, path[1]); empty(x, 204); }
            else throw methodError();
        } else throw ApiException.missing();
    }
    private void analyses(HttpExchange x, String method, String[] path, Map<String,String> query, Service.Actor actor) throws Exception {
        if (path.length == 1) {
            if (method.equals("GET")) json(x, 200, service.analyses(actor, page(query), size(query)));
            else if (method.equals("POST")) {
                limiter.take("analysis:" + actor.userId(), 30);
                json(x, 202, service.createAnalysis(actor, body(x, false), oneHeader(x, "Idempotency-Key")));
            } else throw methodError();
        } else if (path.length == 2 && !path[1].isEmpty()) {
            requireMethod(method, "GET"); json(x, 200, service.analysis(actor, path[1], true));
        } else if (path.length == 3 && !path[1].isEmpty()) {
            if (path[2].equals("status")) {
                requireMethod(method, "GET"); json(x, 200, service.analysis(actor, path[1], false));
            } else if (path[2].equals("cancel")) {
                requireMethod(method, "POST"); body(x, true); json(x, 200, service.cancel(actor, path[1]));
            } else if (path[2].equals("reviews")) {
                if (method.equals("GET")) json(x, 200, service.reviews(actor, path[1], page(query), size(query)));
                else if (method.equals("POST")) json(x, 201, service.review(actor, path[1], body(x, false)));
                else throw methodError();
            } else throw ApiException.missing();
        } else throw ApiException.missing();
    }
    private void reports(HttpExchange x, String[] path, Service.Actor actor) throws Exception {
        if (path.length < 2 || path.length > 3 || path[1].isEmpty()) throw ApiException.missing();
        String format = path.length == 2 ? "json" : path[2];
        if (!Set.of("json", "html", "csv", "pdf").contains(format)) throw ApiException.missing();
        limiter.take("report:" + actor.userId(), 60);
        JsonObject snapshot = service.report(actor, path[1]);
        x.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; sandbox");
        x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"report." + format + "\"");
        switch (format) {
            case "json" -> json(x, 200, snapshot);
            case "html" -> send(x, 200, "text/html; charset=utf-8", Reports.html(snapshot).getBytes(StandardCharsets.UTF_8));
            case "csv" -> send(x, 200, "text/csv; charset=utf-8", Reports.csv(snapshot).getBytes(StandardCharsets.UTF_8));
            case "pdf" -> {
                            if (!pdfExports.tryAcquire()) throw ApiException.limit("PDF export capacity reached; retry shortly");
                            try { send(x, 200, "application/pdf", Reports.pdf(snapshot)); }
                            finally { pdfExports.release(); }
                        }
            default -> throw ApiException.missing();
        }
    }
    private void sameOrigin(HttpExchange x, boolean mutation) {
        String supplied = oneHeader(x, "Origin");
        String site = oneHeader(x, "Sec-Fetch-Site");
        if (site != null && !Set.of("same-origin", "none").contains(site))
            throw new ApiException(403, "CSRF_REJECTED", "Same-origin requests required");
        // Strict Origin validation is the CSRF defense, including raw uploads. No CORS is enabled.
        if (supplied != null && !origin.equals(supplied) || mutation && supplied == null)
            throw new ApiException(403, "CSRF_REJECTED", "A matching Origin header is required");
    }
    private static void headers(HttpExchange x) {
        var h = x.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff"); h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer"); h.set("Cache-Control", "no-store");
        h.set("Cross-Origin-Resource-Policy", "same-origin");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        h.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }
    private void serveStatic(HttpExchange x, String path) throws IOException {
        if (!STATIC.contains(path)) throw ApiException.missing();
        String resource = path.equals("/") ? "/index.html" : path.equals("/favicon.ico") ? "/favicon.svg" : path;
        try (InputStream in = OriginalityDeskServer.class.getResourceAsStream("/web" + resource)) {
            if (in == null) throw ApiException.missing();
            String type = resource.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : resource.endsWith(".css") ? "text/css; charset=utf-8"
                    : resource.endsWith(".svg") ? "image/svg+xml"
                    : "text/html; charset=utf-8";
            send(x, 200, type, in.readNBytes(2 * 1024 * 1024));
        }
    }
    private void setCookie(HttpExchange x, String value, boolean clear) {
        x.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + (clear ? "0" : Service.SESSION_MILLIS / 1000) + (config.secureCookies() ? "; Secure" : ""));
    }
    private static String cookie(HttpExchange x) {
        String found = null;
        for (String header : x.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String[] pair = part.strip().split("=", 2);
                if (pair.length == 2 && pair[0].equals(COOKIE)) {
                    if (found != null) throw ApiException.bad("Duplicate session cookie");
                    found = pair[1];
                }
            }
        }
        return found;
    }
    private static String oneHeader(HttpExchange x, String name) {
        List<String> values = x.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() != 1) throw ApiException.bad("Duplicate " + name + " header");
        return values.get(0);
    }
    private static byte[] readBounded(HttpExchange x, int max) throws IOException {
        String length = oneHeader(x, "Content-Length");
        if (length != null) {
            try {
                long count = Long.parseLong(length);
                if (count < 0) throw ApiException.bad("Invalid Content-Length");
                // HttpServer sends 100 Continue before dispatch. Read only the bounded sentinel
                                // below before replying, so ordinary just-over-limit uploads can receive the JSON 413.
            } catch (NumberFormatException e) { throw ApiException.bad("Invalid Content-Length"); }
        }
        String encoding = oneHeader(x, "Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new ApiException(415, "UNSUPPORTED_ENCODING", "Compressed request bodies are not accepted");
        byte[] bytes = x.getRequestBody().readNBytes(max + 1);
        if (bytes.length > max) throw new ApiException(413, "BODY_TOO_LARGE", "Request body exceeds size limit");
        return bytes;
    }
    private static JsonObject body(HttpExchange x, boolean emptyAllowed) throws IOException {
        String contentType = oneHeader(x, "Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/json"))
            throw new ApiException(415, "JSON_REQUIRED", "Content-Type must be application/json");
        byte[] bytes = readBounded(x, 16 * 1024);
        if (bytes.length == 0 && emptyAllowed) return new JsonObject();
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                reader.setLenient(false);
                JsonElement value = strictJson(reader, 0);
                if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw ApiException.bad("Expected one JSON object");
                return value.getAsJsonObject();
            }
        } catch (IOException | IllegalStateException | NumberFormatException e) { throw ApiException.bad("Invalid JSON body"); }
    }
    private static JsonElement strictJson(JsonReader reader, int depth) throws IOException {
        if (depth > 10) throw ApiException.bad("JSON nesting limit exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw ApiException.bad("Duplicate JSON field");
                    object.add(key, strictJson(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(strictJson(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String value = reader.nextString();
                if (value.length() > 128) throw ApiException.bad("JSON number too long");
                yield new JsonPrimitive(new BigDecimal(value));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw ApiException.bad("Invalid JSON body");
        };
    }
    private static Map<String,String> query(String raw) {
        Map<String,String> result = new HashMap<>();
        if (raw == null) return result;
        if (raw.length() > 4096) throw ApiException.bad("Query too long");
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            try {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length == 1 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (result.putIfAbsent(key, value) != null) throw ApiException.bad("Duplicate query parameter");
            } catch (IllegalArgumentException e) { throw ApiException.bad("Malformed query"); }
        }
        return result;
    }
    private static int page(Map<String,String> query) { return positive(query.getOrDefault("page", "1"), 1_000_000); }
    private static int size(Map<String,String> query) { return positive(query.getOrDefault("size", "20"), 100); }
    private static int positive(String value, int max) {
        try {
            if (!value.matches("[0-9]{1,7}")) throw ApiException.bad("Invalid pagination");
            int number = Integer.parseInt(value);
            if (number < 1 || number > max) throw ApiException.bad("Pagination out of range");
            return number;
        } catch (NumberFormatException e) { throw ApiException.bad("Invalid pagination"); }
    }
    private static void requireMethod(String actual, String expected) { if (!actual.equals(expected)) throw methodError(); }
    private static ApiException methodError() { return new ApiException(405, "METHOD_NOT_ALLOWED", "Method not allowed"); }
    private static void json(HttpExchange x, int status, Object body) throws IOException {
        send(x, status, "application/json; charset=utf-8", Service.JSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }
    private static void send(HttpExchange x, int status, String type, byte[] body) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.sendResponseHeaders(status, body.length);
        try (OutputStream output = x.getResponseBody()) { output.write(body); }
    }
    private static void empty(HttpExchange x, int status) throws IOException { x.sendResponseHeaders(status, -1); }

    @Override public synchronized void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        try {
            http.stop(1); requests.shutdownNow();
            requests.awaitTermination(5, TimeUnit.SECONDS);
        } finally { try { worker.close(); } finally { database.close(); } }
    }
}
