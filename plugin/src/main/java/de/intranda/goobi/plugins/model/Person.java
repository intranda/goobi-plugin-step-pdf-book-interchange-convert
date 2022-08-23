package de.intranda.goobi.plugins.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Person {
    private String mets;
    private String firstName;
    private String lastName;
}
