package com.github.akmakm.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.Reader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads configuration file.
 * 
 * @author alka
 */
public class Configuration {
    
    /**
     * File to read.
     */
    private final File file;
    
    /**
     * Settings and values.
     */
    private final Map<String, String> settings = new HashMap<>();
    
    /**
     * Constructs a new reader for the specified filename.
     * 
     * @param file filename
     */
    public Configuration(String file) {
        this.file = new File(file);
    }
    
    /**
     * Constructs a new reader for the specified file.
     * 
     * @param file file
     */
    public Configuration(File file) {
        this.file = file;
    }
    
    /**
     * Reads configuration file.
     * 
     * @throws IOException if error reading file
     * @throws JsonSyntaxException if error parsing file
     */
    public void read() throws IOException, JsonSyntaxException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file)
                                                   , "UTF-8")) {
            settings.putAll(new Gson().fromJson(reader, HashMap.class));
        }  
    }
    
    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }
    
    /**
     * Gets value for given setting, or {@code null} if no value specified in
     * configuration file.
     * 
     * @param key
     * @return 
     */
    public String get(String key) {
        return settings.get(key);
    }
    
    /**
     * Gets value for given setting, or {@code defaultValue} if no value
     * specified in configuration file.
     * 
     * @param key
     * @param defaultValue
     * @return 
     */
    public String getOrDefault(String key, String defaultValue) {
        String val=settings.get(key);
        return (val==null?defaultValue:val);
    }
    
    @Override
    public String toString() {
        return String.format("[Configuration: file=%s]", file);
    }
}