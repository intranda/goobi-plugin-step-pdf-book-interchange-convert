package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import ugh.dl.Person;

@Data
@RequiredArgsConstructor
public class BookPart {
    @NonNull
    private ParsedMetadata metadata;
    @NonNull
    private int firstPage;
    @NonNull
    private int lastPage;
    private List<BookPart> bookParts = new ArrayList<>();
}
