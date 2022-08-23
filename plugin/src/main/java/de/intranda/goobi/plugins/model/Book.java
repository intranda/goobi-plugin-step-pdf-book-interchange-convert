package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Book {
    private Metadata metadata;
    private List<BookPart> bookParts = new ArrayList<>();
    
    public void addBookPart(BookPart part) {
        this.bookParts.add(part);
    }
}

