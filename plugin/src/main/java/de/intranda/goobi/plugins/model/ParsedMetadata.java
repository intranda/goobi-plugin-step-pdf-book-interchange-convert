package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParsedMetadata {
    private List<ParsedPerson> persons = new ArrayList<>();
    private List<MetadataElement> metadata = new ArrayList<>();
}
