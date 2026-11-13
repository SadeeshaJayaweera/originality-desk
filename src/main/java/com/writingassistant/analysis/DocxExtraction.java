package com.writingassistant.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class DocxExtraction {
    private static final String TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String STRICT_WORD = "http://purl.oclc.org/ooxml/wordprocessingml/main";
    private static final String MAIN_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";

    private DocxExtraction() { }

    static DocumentExtractor.ExtractedDocument extract(byte[] content) throws IOException {
        Map<String, byte[]> entries = SafeZip.read(content);
        require(entries, "[Content_Types].xml");
        require(entries, "_rels/.rels");
        require(entries, "word/document.xml");
        boolean[] mainType = {false};
        boolean[] mainRelationship = {false};
        for (var entry : entries.entrySet()) {
            String name = entry.getKey();
            String lower = name.toLowerCase(Locale.ROOT);
            if (unsafe(lower) || lower.matches(".*\\.(bin|zip|exe|dll|js|docm|xlsm|pptm|pdf)$")
                    || DocumentExtractor.zipMagic(entry.getValue())) {
                throw new IOException("DOCX contains macros, active content or embedded packages");
            }
            if (!lower.endsWith(".xml") && !lower.endsWith(".rels")) continue;
            if (lower.endsWith(".rels") && !name.equals("_rels/.rels") && !name.matches("(?:.*/)?_rels/[^/]+\\.rels")) {
                throw new IOException("Invalid OOXML relationship part path");
            }
            Set<String> relationshipIds = new HashSet<>();
            parse(entry.getValue(), new GuardedHandler() {
                @Override
                void start(String uri, String local, Attributes a) throws SAXException {
                    if (unsafe(local.toLowerCase(Locale.ROOT))) throw new SAXException("Active DOCX markup is forbidden");
                    if (name.equals("[Content_Types].xml")) {
                        if (depth == 1 && (!uri.equals(TYPES) || !local.equals("Types"))) {
                            throw new SAXException("Invalid OOXML content types");
                        }
                        String type = a.getValue("ContentType");
                        if (type != null && unsafe(type.toLowerCase(Locale.ROOT))) throw new SAXException("Unsafe DOCX content type");
                        if (uri.equals(TYPES) && local.equals("Override")
                                && "/word/document.xml".equals(a.getValue("PartName"))) {
                            if (depth != 2 || mainType[0] || !MAIN_TYPE.equals(type)) throw new SAXException("Invalid or duplicate DOCX main content type");
                            mainType[0] = true;
                        }
                    }
                    if (lower.endsWith(".rels")) {
                        if (depth == 1 && (!uri.equals(RELS) || !local.equals("Relationships"))) {
                            throw new SAXException("Invalid OOXML relationships");
                        }
                        if (!uri.equals(RELS) || !local.equals("Relationship")) return;
                        if (depth != 2) throw new SAXException("Invalid nested OOXML relationship");
                        String mode = a.getValue("TargetMode");
                        String type = a.getValue("Type");
                        String id = a.getValue("Id");
                        if (id == null || !relationshipIds.add(id) || type == null || unsafe(type.toLowerCase(Locale.ROOT))
                                || (mode != null && !mode.equals("Internal"))) {
                            throw new SAXException("External or active DOCX relationships are forbidden (including external hyperlinks)");
                        }
                        String target = resolveTarget(name, a.getValue("Target"));
                        if (!entries.containsKey(target)) throw new SAXException("DOCX relationship target is missing");
                        if (name.equals("_rels/.rels") && (type.equals("http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument")
                                || type.equals("http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument"))) {
                            if (mainRelationship[0] || !target.equals("word/document.xml")) throw new SAXException("Unsupported or duplicate DOCX main part");
                            mainRelationship[0] = true;
                        }
                    }
                }
            });
        }
        if (!mainType[0] || !mainRelationship[0]) throw new IOException("ZIP is not a genuine non-macro OOXML Word document");
        StringBuilder text = new StringBuilder();
        boolean[] bodySeen = {false};
        parse(entries.get("word/document.xml"), new GuardedHandler() {
            private boolean inText;
            private boolean body;
            @Override
            void start(String uri, String local, Attributes a) throws SAXException {
                boolean word = uri.equals(WORD) || uri.equals(STRICT_WORD);
                if (depth == 1 && (!word || !local.equals("document"))) throw new SAXException("Invalid OOXML Word document root");
                if (!word) return;
                if (local.equals("body")) {
                    if (depth != 2 || bodySeen[0]) throw new SAXException("Invalid or duplicate OOXML document body");
                    bodySeen[0] = true;
                    body = true;
                }
                if (!body) return;
                if (local.equals("t")) inText = true;
                if (local.equals("tab")) append("\t");
                if (local.equals("br") || local.equals("cr")) append("\n");
            }
            @Override
            public void characters(char[] chars, int start, int length) throws SAXException {
                if (inText) append(new String(chars, start, length));
            }
            @Override
            void end(String uri, String local) throws SAXException {
                if (!uri.equals(WORD) && !uri.equals(STRICT_WORD)) return;
                if (local.equals("t")) inText = false;
                if (body && local.equals("p")) append("\n");
                if (body && local.equals("tc")) append("\t");
                if (local.equals("body")) body = false;
            }
            private void append(String value) throws SAXException {
                if (text.length() + value.length() > DocumentExtractor.MAX_CHARACTERS) throw new SAXException("Extracted text exceeds the 200000 character limit");
                text.append(value);
            }
        });
        if (!bodySeen[0]) throw new IOException("OOXML Word document body is missing");
        return new DocumentExtractor.ExtractedDocument(text.toString(), 0, "DOCX");
    }

    private static boolean unsafe(String value) {
        return value.contains("macro") || value.contains("vba") || value.contains("activex")
                || value.contains("oleobject") || value.contains("embeddings") || value.contains("attachedtemplate")
                || value.contains("afchunk") || value.contains("altchunk") || value.contains("javascript")
                || value.contains("/package") || value.equals("object") || value.equals("control");
    }

    private static String resolveTarget(String relationshipPart, String target) throws SAXException {
        try {
            if (target == null || target.isBlank() || target.contains("\\") || target.contains("%")) throw new URISyntaxException("", "Unsafe target");
            URI uri = new URI(target);
            if (uri.isAbsolute() || uri.getAuthority() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new URISyntaxException(target, "External target");
            }
            String base = relationshipPart.equals("_rels/.rels") ? "" : relationshipPart.substring(0, relationshipPart.lastIndexOf("_rels/"));
            String path = new URI("/" + base).resolve(uri).normalize().getPath();
            if (path == null || path.contains("/../") || path.endsWith("/..")) throw new URISyntaxException(target, "Escaping target");
            return path.substring(1);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new SAXException("Unsafe DOCX relationship target", e);
        }
    }

    private static void require(Map<String, byte[]> entries, String name) throws IOException {
        if (!entries.containsKey(name)) throw new IOException("DOCX is missing required OOXML part " + name);
    }

    private static void parse(byte[] xml, GuardedHandler handler) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External XML resources are forbidden"); });
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(xml)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Invalid or unsafe DOCX XML: " + e.getMessage(), e);
        }
    }

    private abstract static class GuardedHandler extends DefaultHandler {
        int depth;
        private int elements;
        @Override
        public final void startElement(String uri, String local, String qualified, Attributes a) throws SAXException {
            if (++depth > 128 || ++elements > 100_000 || a.getLength() > 64) throw new SAXException("DOCX XML complexity limit exceeded");
            start(uri, local, a);
        }
        @Override
        public final void endElement(String uri, String local, String qualified) throws SAXException {
            end(uri, local);
            depth--;
        }
        void start(String uri, String local, Attributes a) throws SAXException { }
        void end(String uri, String local) throws SAXException { }
    }
}
