package com.koibots.scout.hub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A QR Scout field.
 */
public class Field {
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean getRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFormResetBehavior() {
        return formResetBehavior;
    }

    public void setFormResetBehavior(String formResetBehavior) {
        this.formResetBehavior = formResetBehavior;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public void setChoices(Map<String, String> choices) {
        if(null == choices) {
            choices = null;
        } else {
            // Use LinkedHashMap to keep choices in order
            this.choices = new LinkedHashMap<>(choices);
        }
    }

    public Map<String,String> getChoices() {
        if(null == choices) {
            return null;
        } else {
            return Collections.unmodifiableMap(choices);
        }
    }

    public void copyTo(Field field) {
        field.setTitle(getTitle());
        field.setType(getType());
        field.setDescription(getDescription());
        field.setRequired(getRequired());
        field.setCode(getCode());
        field.setFormResetBehavior(getFormResetBehavior());
        field.setDefaultValue(getDefaultValue());
        field.setMin(getMin());
        field.setMax(getMax());
        field.setStep(getStep());
        field.setOutputType(getOutputType());
        field.setChoices(getChoices());
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