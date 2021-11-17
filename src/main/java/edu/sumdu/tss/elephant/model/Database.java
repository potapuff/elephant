package edu.sumdu.tss.elephant.model;

import lombok.Data;

@Data
public class Database {
    public static final int NAME_SIZE = 10;
    private String name;
    private String owner;
}
