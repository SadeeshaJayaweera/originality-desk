package com.writingassistant.core.citation;

import java.util.UUID;

/**
 * Data model representing an original quoted passage and its associated citation metadata.
 */
public class SourceQuote {

    private String id;
    private String quote;
    private String author;
    private String year;
    private String title;
    private String publication;
    private String volume;
    private String issue;
    private String pageNumber;
    private String urlOrDoi;
    private SourceType sourceType;

    public SourceQuote() {
        this.id = UUID.randomUUID().toString();
        this.sourceType = SourceType.OTHER;
    }

    public SourceQuote(String quote, String author, String year, String title, String publication) {
        this.id = UUID.randomUUID().toString();
        this.quote = quote;
        this.author = author;
        this.year = year;
        this.title = title;
        this.publication = publication;
        this.sourceType = SourceType.OTHER;
    }

    public SourceQuote(String quote, String author, String year, String title, String publication,
                       String pageNumber, String urlOrDoi, SourceType sourceType) {
        this.id = UUID.randomUUID().toString();
        this.quote = quote;
        this.author = author;
        this.year = year;
        this.title = title;
        this.publication = publication;
        this.pageNumber = pageNumber;
        this.urlOrDoi = urlOrDoi;
        this.sourceType = (sourceType != null) ? sourceType : SourceType.OTHER;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublication() {
        return publication;
    }

    public void setPublication(String publication) {
        this.publication = publication;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(String pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getUrlOrDoi() {
        return urlOrDoi;
    }

    public void setUrlOrDoi(String urlOrDoi) {
        this.urlOrDoi = urlOrDoi;
    }

    public SourceType getSourceType() {
        return (sourceType != null) ? sourceType : SourceType.OTHER;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    @Override
    public String toString() {
        return "SourceQuote{" +
                "author='" + author + '\'' +
                ", year='" + year + '\'' +
                ", title='" + title + '\'' +
                ", sourceType=" + sourceType +
                '}';
    }
}
