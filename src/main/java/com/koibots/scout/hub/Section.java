package com.koibots.scout.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Models a section or phase of a game.
 */
public class Section {
    private String name;
    private ArrayList<Field> fields;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Field> getFields() {
        if(null == fields) {
            return null;
        } else {
            return Collections.unmodifiableList(fields);
        }
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