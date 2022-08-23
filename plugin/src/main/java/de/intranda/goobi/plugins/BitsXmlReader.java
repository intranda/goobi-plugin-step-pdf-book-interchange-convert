package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import de.intranda.goobi.plugins.PdfBookInterchangeConvertStepPlugin.MetadataMapping;
import de.intranda.goobi.plugins.PdfBookInterchangeConvertStepPlugin.PersonMapping;
import de.intranda.goobi.plugins.model.Book;
import de.intranda.goobi.plugins.model.BookPart;
import de.intranda.goobi.plugins.model.Metadata;
import de.intranda.goobi.plugins.model.MetadataElement;
import de.intranda.goobi.plugins.model.Person;

public class BitsXmlReader {
    private Book book;
    private Document jdomDocument;
    private XPathFactory xpathFactory = XPathFactory.instance();
    private String bookPartXpath;

    public BitsXmlReader(Path xmlBitsFile, String bookPartXpath) {
        SAXBuilder jdomBuilder = new SAXBuilder();
        this.bookPartXpath = bookPartXpath;
        // jdomBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // jdomBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        // jdomBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        if (xmlBitsFile != null && Files.exists(xmlBitsFile)) {
            try {
                this.jdomDocument = jdomBuilder.build(xmlBitsFile.toString());
            } catch (JDOMException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    
    /**
     * Reads the Metadata from the Bits-XML-File and returns a Book Object
     * @param publicationMetadata List with metadata mappings of elements for the TopStruct that shall be read
     * @param publicationPersons List with person metadata mappings of elements for the TopStruct that shall be read
     * @param elementMetadata List with metadata mapping elements for childs of the TopStruct that shall be read
     * @param elementPersons List with person metadata mappings for childs of the TopStruct that shall be read
     * @param fpageXpath xpath that specifies the path to the fpage-Element
     * @param lpageXpath xpath that specifies the path to the lpage-Element
     * @return
     */
    public Book readXml(List<MetadataMapping> publicationMetadata, List<PersonMapping> publicationPersons, List<MetadataMapping> elementMetadata,List<PersonMapping> elementPersons, String fpageXpath, String lpageXpath ) {
        Book result = new Book();
        // read TopStruct Metadata
        List<MetadataElement> bookMetadata = readMetada(publicationMetadata, jdomDocument);
        List<Person> bookPersons = readPersons(publicationPersons, jdomDocument);
        result.setMetadata(new Metadata(bookPersons, bookMetadata));
        
        XPathExpression<Object> bookPartXpathExpr = this.xpathFactory.compile(this.bookPartXpath);
        List<Object> bookPartNodeObjects = bookPartXpathExpr.evaluate(jdomDocument);
        for (Object bookPartNode : bookPartNodeObjects) {
            List<MetadataElement> bookPartMetadataElements = readMetada(elementMetadata, bookPartNode);
            List<Person> bookPartPersons = readPersons(elementPersons, bookPartNode);
            Metadata bookPartMetadata = new Metadata(bookPartPersons, bookPartMetadataElements);
            String lpage = readFirstValue(lpageXpath, bookPartNode);
            String fpage = readFirstValue(fpageXpath, bookPartNode);
            
            //TODO add Error Message on failure!
            if (StringUtils.isNotBlank(fpage)&& StringUtils.isNotBlank(lpage)) {
                result.addBookPart(new BookPart(bookPartMetadata, Integer.parseInt(fpage.trim()), Integer.parseInt(lpage.trim())));
            }          
        }
        
        return result;
    }
    
    private List<MetadataElement> readMetada(List<MetadataMapping> metadaMappings, Object source) {
        List<MetadataElement> metadataElements= new ArrayList<>();
        for (MetadataMapping metadataMapping : metadaMappings) {
            XPathExpression<Object> XpathExpr = this.xpathFactory.compile(metadataMapping.getXpath());
            List<Object> metadataObjects = XpathExpr.evaluate(source);
            List<String> readValues = readValues(metadataObjects);
            for (String readValue : readValues) {
                metadataElements.add(new MetadataElement(metadataMapping.getMets(), readValue));
            }
        }
        return metadataElements;
    }

    private List<Person> readPersons(List<PersonMapping> personMappings, Object source) {
        List <Person> persons = new ArrayList<>();
        for (PersonMapping personMapping : personMappings) {
            XPathExpression<Object> XpathExpr = this.xpathFactory.compile(personMapping.getXpathNode());
            List<Object> personNodeObjects = XpathExpr.evaluate(source);
            for (Object personNodeObject: personNodeObjects  ) {
                XPathExpression<Object> firstnameXpathExpr = this.xpathFactory.compile(personMapping.getXpathFirstname());
                String firstname = readFirstValue (personMapping.getXpathFirstname(), personNodeObject);
                String lastname  = readFirstValue (personMapping.getXpathLastname(), personNodeObject);
                persons.add(new Person(personMapping.getMets(), firstname, lastname));
            }
        }
        return persons;
    }
    
    private String readFirstValue (String xpathString, Object source) {
        XPathExpression<Object> nameXpathExpr = this.xpathFactory.compile(xpathString);
        List<Object> Objects = nameXpathExpr.evaluate(source);
        List<String> readValues = readValues(Objects);
        if (readValues.size()>=1) {
            return readValues.get(0);
        }
        return "";
    }

    private List<String> readValues(List<Object> elements) {
        List<String> readValues = new ArrayList<>();
        for (Object val : elements) {
            if (val instanceof Element) {
                val = ((Element) val).getTextTrim();
            } else if (val instanceof Attribute) {
                val = ((Attribute) val).getValue();
            } else if (val instanceof Text) {
                val = ((Text) val).getText();
            } else if (val != null && !(val instanceof String)) {
                val = val.toString();
            }
            if (val instanceof String) {
                readValues.add((String)val);
            }
        }
        return readValues;
    }
}
