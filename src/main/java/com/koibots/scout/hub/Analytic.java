package com.koibots.scout.hub;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;

public class Analytic {

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

    public static Analytic loadAnalytic(File file) throws IOException {
        Analytic analytic = new Analytic();
        analytic.loadFromFile(file);
        return analytic;
    }

    private void loadFromFile(File file) throws IOException {
        Gson gson = new Gson();

        try(FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {

            //
            // Config file structure
            //
            //   { "name" : anyliticName,
            //     "query" : sqlQuery }.
            //

            Map<?, ?> data = gson.fromJson(reader, Map.class);

            setName((String)data.get("name"));
            setQuery((String)data.get("query"));
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(loadAnalytic(new File(args[0])));
    }
}