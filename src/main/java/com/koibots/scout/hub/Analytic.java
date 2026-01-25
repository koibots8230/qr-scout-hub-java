package com.koibots.scout.hub;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A named query for performing one kind of analysis on game data.
 */
public class Analytic
{
    private String filename;
    private String name;
    private String query;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Override
    public String toString() {
        return getName() + ":" + getQuery();
    }

    @Override
    public boolean equals(Object o) {
        if(null == o || !o.getClass().equals(this.getClass())) {
            return false;
        }
        Analytic a = (Analytic)o;

        return Objects.equals(getName(), a.getName())
                && Objects.equals(getQuery(), a.getQuery())
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getQuery());
    }

    /**
     * Loads an analytic from a file.
     *
     * @param file The file to load the analytic from.
     *
     * @throws IOException If there is a problem reading from the input file.
     */
    private void loadFromFile(File file) throws IOException {
        Gson gson = new Gson();

        try(FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Map<?, ?> data = gson.fromJson(reader, Map.class);

            setFilename(file.getName());
            setName((String)data.get("name"));
            setQuery((String)data.get("query"));
        }
    }

    /**
     * Saves an analytic to a file.
     *
     * @param file The file to save the analytic to.
     *
     * @throws IOException If there is a problem saving the analytic to the file.
     */
    public void saveToFile(File file)
        throws IOException
    {
        Gson gson = new GsonBuilder()
                .addSerializationExclusionStrategy(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return f.getName().equals("filename");
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .disableHtmlEscaping() // Don't escape = and ' characters
                .setPrettyPrinting() // Write semi-human-readable JSON
                .create();

        try (FileWriter out = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(this, out);
        }
    }

    /**
     * Factory method to load an analytic from a file.
     *
     * The file is a JSON file with the following structure:
     *
     * <pre>
     * {
     *   "name" : anyliticName,
     *   "query" : sqlQuery
     * }
     *
     * @param file The file to load the analytic from.
     *
     * @return The analytic loaded from the file.
     *
     * @throws IOException If there is a problem reading from the input file.
     */
    public static Analytic loadAnalytic(File file) throws IOException {
        Analytic analytic = new Analytic();
        analytic.loadFromFile(file);
        return analytic;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(loadAnalytic(new File(args[0])));
    }
}