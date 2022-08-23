package de.intranda.goobi.plugins.model;

import java.util.HashMap;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import ugh.dl.Person;

@Data
@AllArgsConstructor
public class BookPart {
    private Metadata metadata;
    private int firstPage;
    private int lastPage;
}
