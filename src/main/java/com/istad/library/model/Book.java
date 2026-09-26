package com.istad.library.model;

import java.util.Locale;
import java.util.Objects;

/** A title in the catalogue together with its copy counts. */
public final class Book {
    private final String id;
    private String title;
    private String author;
    private String category;
    private int quantity;
    private int available;

    public Book(String id, String title, String author, String category, int quantity, int available) {
        this.id = clean(id);
        this.title = clean(title);
        this.author = clean(author);
        this.category = clean(category);
        this.quantity = quantity;
        this.available = available;
    }

    public Book(Book source) {
        this(source.id, source.title, source.author, source.category, source.quantity, source.available);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = clean(title);
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = clean(author);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = clean(category);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return id + " — " + title + " (" + available + " available)";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Book other)) {
            return false;
        }
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id.toUpperCase(Locale.ROOT));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
