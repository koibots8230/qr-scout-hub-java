package com.koibots.scout.hub;

import java.io.File;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * The game configuration for an FRC game.
 */
public class GameConfig {
    /**
     * A QR Scout field.
     */
    public static class Field {
        private String title;
        private String description;
        private String type;
        private boolean required;
        private String code;
        private String defaultValue;

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public boolean getRequired() {
            return required;
        }

        public String getCode() {
            return code;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        @Override
        public boolean equals(Object o) {
            return null != o
                    && getClass().equals(o.getClass())
                    && getCode().equals(((Field)o).getCode())
                    ;
        }

        @Override
        public int hashCode() {
            return getCode().hashCode();
        }

        @Override
        public String toString() {
            return "Field { title=" + getTitle() + ", code=" + getCode() + ", type=" + getType() + ", required=" + getRequired() + " }";
        }
    }

    /**
     * The "page title" for QR Scout. Usually the name of the game.
     */
    private String pageTitle;

    /**
     * The scouting fields for the game.
     */
    private List<Field> fields;

    private GameConfig() {
        // Require clients to use Factory method
    }

    /**
     * Gets the QR Scout page title. This is usually the name of the game.
     *
     * @return The QR Scout's page title.
     */
    public String getPageTitle() {
        return pageTitle;
    }

    /**
     * Gets the scouting fields.
     *
     * @return The List of scouting Fields.
     */
    public List<Field> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "GameConfig { fields=" + getFields() + " }";
    }

    /**
     * Reads a game configuration from a JSON file.
     *
     * @param file The file to read.
     *
     * @return A GameConfig configured with the information in the file.
     *
     * @throws IOException If there is a problem reading the file.
     */
    public static GameConfig readFile(File file) throws IOException {
        return readURL(file.toURI().toURL());
    }

    /**
     * Reads a game configuration from a JSON file.
     *
     * @param url The resource to read.
     *
     * @return A GameConfig configured with the information in the resource.
     *
     * @throws IOException If there is a problem reading the resource.
     */
    public static GameConfig readURL(URL url) throws IOException {
        Gson gson = new Gson();

        Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);

        //
        // Config file structure
        //
        // sections : [
        //   { "name" : sectionName,
        //     "fields" : [
        //       { "title", ...
        //     ]
        // ]

        Map<?, ?> data = gson.fromJson(reader, Map.class);

        Object o = data.get("page_title");

        if(null == o || !(o instanceof String)) {
            throw new IOException("Config has no 'page_title' property");
        }

        GameConfig config = new GameConfig();

        config.pageTitle = (String)o;

        o = data.get("sections");
        if(null == o) {
            throw new IOException("Config file has no 'sections' property");
        }

        if(!(o instanceof Collection<?>)) {
            throw new IOException("Config file contains suspicious 'sections' property: expected Collection, got type=" + o.getClass().getName());
        }

        ArrayList<Field> fields = new ArrayList<Field>();

        for(Object section : (Collection<?>)o) {
            if(!(section instanceof Map<?,?>)) {
                throw new IOException("Config file contains suspicious 'section': expected Map, got type=" + section.getClass().getName());
            }

            o = ((Map<?,?>)section).get("name");

            if(!(o instanceof String)) {
                throw new IOException("Config file contains section with no name");
            }

            String sectionName = (String)o;

            o = ((Map<?,?>)section).get("fields");

            if(null == o) {
                throw new IOException("Section " + sectionName + " contains no fields");
            }
            if(!(o instanceof Collection<?>)) {
                throw new IOException("Config file section " + sectionName + " contains suspicious 'fields': expected Collection, got type=" + o.getClass().getName());
            }

            for(Object field : (Collection<?>)o) {
                if(!(field instanceof Map<?,?>)) {
                    throw new IOException("Config file section " + sectionName + " contains suspicious field: expected Map, got type=" + field.getClass().getName());
                }

                data = (Map<?,?>)field;

                Field f = new Field();
                f.title = (String)data.get("title");
                f.description = (String)data.get("description");
                f.type = (String)data.get("type");
                f.required = Boolean.TRUE.equals(data.get("required"));
                f.code = (String)data.get("code");
                f.defaultValue = String.valueOf(data.get("defaultValue"));

                fields.add(f);
            }
        }

        config.fields = fields;

        return config;
    }

    public static void main(String[] args) throws Exception {
        if(0 == args.length) {
            System.err.println("Usage: " + GameConfig.class.getName() + " <jsonfile>");

            System.exit(1);
        }

        GameConfig config = GameConfig.readFile(new File(args[0]));

        System.out.println("Game: " + config.getPageTitle());
        System.out.println("Scouting Fields:");
        for(Field field : config.getFields()) {
            System.out.println("  " + field.getTitle());
        }
    }
}
