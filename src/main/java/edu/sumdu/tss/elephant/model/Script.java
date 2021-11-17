package edu.sumdu.tss.elephant.model;

import lombok.Data;

import java.util.Date;

@Data
public class Script {
    private long id;
    private String filename;
    private String path;
    private String database;
    private String description;
    private long size;
    private Date createdAt;
    private Date updatedAt;
}
