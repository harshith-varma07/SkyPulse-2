package com.air.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAQResponse {
    private List<OpenAQResult> results;
    
    public List<OpenAQResult> getResults() { return results; }
    public void setResults(List<OpenAQResult> results) { this.results = results; }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAQResult {
        private String city;
        private String country;
        private List<Measurement> measurements;
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        
        public List<Measurement> getMeasurements() { return measurements; }
        public void setMeasurements(List<Measurement> measurements) { this.measurements = measurements; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Measurement {
        private String parameter;
        private Double value;
        private String unit;
        
        public String getParameter() { return parameter; }
        public void setParameter(String parameter) { this.parameter = parameter; }
        
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
        
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Object getDate() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getDate'");
        }
    }
}