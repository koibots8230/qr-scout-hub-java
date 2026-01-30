package com.koibots.scout.hub;

import java.io.File;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        private String formResetBehavior;
        private Object defaultValue;

        // Field-type-specific properties

        // For numerics: use Integer class instead of primitive
        // because the value can be "none"
        private Integer min;
        private Integer max;
        private Integer step;

        // For timers
        private String outputType;

        // For selects
        private Map<String,String> choices;

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

        public String getFormResetBehavior() {
            return formResetBehavior;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public Integer getMin() {
            return min;
        }

        public Integer getMax() {
            return max;
        }

        public Integer getStep() {
            return step;
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
            return "Field { title=" + getTitle() + ", code=" + getCode() + ", type=" + getType() + ", required=" + getRequired() + ", min=" + getMin() + ", max=" + getMax() + ", step=" + getStep() + " }";
        }
    }

    public static class Section {
        private String name;
        private ArrayList<Field> fields;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public List<Field> getFields() {
            return fields;
        }
        public void setFields(List<Field> fields) {
            if(null == fields) {
                this.fields = null;
            } else {
                this.fields = new ArrayList<>(fields);
            }
        }

        @Override
        public String toString() {
            return "Section { name=" + getName() + ", fields=" + getFields() + " }";
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

    /**
     * The sections / phases of the game.
     */
    private List<Section> sections;

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
        return Collections.unmodifiableList(fields);
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }

    public void setSections(List<Section> sections) {
        if(null == sections) {
            this.sections = null;
        } else {
            this.sections = new ArrayList<>(sections);
        }
    }

    @Override
    public String toString() {
        return "GameConfig { fields=" + getFields() + " , sections=" + getSections() + " }";
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

        try(Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
            //
            // Config file structure
            //
            // sections : [
            //   { "name" : name,
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

            ArrayList<Section> sections = new ArrayList<>();
            ArrayList<Field> allFields = new ArrayList<>();

            for(Object section : (Collection<?>)o) {
                if(!(section instanceof Map<?,?>)) {
                    throw new IOException("Config file contains suspicious 'section': expected Map, got type=" + section.getClass().getName());
                }

                o = ((Map<?,?>)section).get("name");

                if(!(o instanceof String)) {
                    throw new IOException("Config file contains section with no name");
                }

                String sectionName = (String)o;

                ArrayList<Field> fields = new ArrayList<>();

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
                    f.formResetBehavior = (String)data.get("formResetBehavior");
                    Object defaultValue = data.get("defaultValue");
                    if(null != defaultValue) {
                        System.out.println("Default value for field '" + f.title + "' is " + defaultValue + " of type " + data.get("defaultValue").getClass());
                        if(defaultValue instanceof Number) {
                            String v = String.valueOf(defaultValue);
                            if(v.endsWith(".0")) {
                                System.out.println("Using Integer");
                                // Let's use an Integer instead
                                defaultValue = ((Number)defaultValue).intValue();
                            }
                        }
                        f.defaultValue = defaultValue;
                        System.out.println("Final default value type for field '" + f.title + "' is " + defaultValue.getClass());
                    }
                    f.outputType = (String)data.get("outputType");
                    o = data.get("min");
                    if(null != o && o instanceof Number) {
                        f.min = ((Number)o).intValue();
                    }
                    o = data.get("max");
                    if(null != o && o instanceof Number) {
                        f.max = ((Number)o).intValue();
                    }
                    o = data.get("step");
                    if(null != o && o instanceof Number) {
                        f.step = ((Number)o).intValue();
                    }
                    o = data.get("choices");
                    if(null != o && o instanceof Map) {
//                        System.out.println("choices is of type " + o.getClass());
                        // Use LinkedHashMap to keep these options IN ORDER
                        @SuppressWarnings("unchecked")
                        LinkedHashMap<String,String> choices = new LinkedHashMap<>((Map<String,String>)o);
                        f.choices = choices;
                    }

                    allFields.add(f);
                    fields.add(f);
                }

                Section s = new Section();
                s.name = sectionName;
                s.setFields(fields);

                sections.add(s);
            }
            config.setSections(sections);

            config.fields = allFields;
            config.sections = sections;

            return config;
        }
    }

    public static void main(String[] args) throws Exception {
        if(0 == args.length) {
            System.err.println("Usage: " + GameConfig.class.getName() + " <jsonfile>");

            System.exit(1);
        }

        GameConfig config = GameConfig.readFile(new File(args[0]));

        System.out.println("Game: " + config.getPageTitle());
        System.out.println("Scouting sections:");
        for(Section section : config.getSections()) {
            System.out.println("  " + section);
        }
    }
}
