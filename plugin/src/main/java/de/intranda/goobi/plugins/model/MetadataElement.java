package de.intranda.goobi.plugins.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetadataElement {
    private String mets;
    private String value;
}
