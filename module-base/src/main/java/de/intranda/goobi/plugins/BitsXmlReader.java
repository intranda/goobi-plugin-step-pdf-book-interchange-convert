package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.goobi.production.enums.LogType;
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
import de.intranda.goobi.plugins.model.ParsedMetadata;
import de.intranda.goobi.plugins.model.MetadataElement;
import de.intranda.goobi.plugins.model.ParsedPerson;

public class BitsXmlReader {
    private Book book;
    private Document jdomDocument;
    private static XPathFactory xpathFactory = XPathFactory.instance();
    private XPathExpression<Object> bookPartXpath;
    private PdfBookInterchangeConvertStepPlugin plugin;

    public BitsXmlReader(Path xmlBitsFile, XPathExpression<Object> bookPartXpath, PdfBookInterchangeConvertStepPlugin plugin) throws JDOMException, IOException {
        SAXBuilder jdomBuilder = new SAXBuilder();
        this.bookPartXpath = bookPartXpath;
        this.plugin = plugin;
        // the bits-xml -files use doctype declaration and external general entities!
        if (xmlBitsFile != null && Files.exists(xmlBitsFile)) {
            this.jdomDocument = jdomBuilder.build(xmlBitsFile.toString());
        }
    }

    /**
     * Reads the Metadata from the Bits-XML-File and returns a Book Object
     * 
     * @param publicationMetadata List with metadata mappings of elements for the TopStruct that shall be read
     * @param publicationPersons List with person metadata mappings of elements for the TopStruct that shall be read
     * @param elementMetadata List with metadata mapping elements for childs of the TopStruct that shall be read
     * @param elementPersons List with person metadata mappings for childs of the TopStruct that shall be read
     * @param fpageXpath xpath that specifies the path to the fpage-Element
     * @param lpageXpath xpath that specifies the path to the lpage-Element
     * @return
     */
    public Book readXml(List<MetadataMapping> publicationMetadata, List<PersonMapping> publicationPersons, List<MetadataMapping> elementMetadata,
            List<PersonMapping> elementPersons,  XPathExpression<Object> fpageXpath,  XPathExpression<Object> lpageXpath) throws IllegalArgumentException {
        Book result = new Book();
        // read TopStruct Metadata
        List<MetadataElement> bookMetadata = readMetada(publicationMetadata, jdomDocument);
        List<ParsedPerson> bookPersons = readPersons(publicationPersons, jdomDocument);
        result.setMetadata(new ParsedMetadata(bookPersons, bookMetadata));

        XPathExpression<Object> bookPartXpathExpr = this.bookPartXpath;
        List<Object> bookPartNodeObjects = bookPartXpathExpr.evaluate(jdomDocument);
        int NoMappingPossibleCounter = 0;
        for (Object bookPartNode : bookPartNodeObjects) {
            List<MetadataElement> bookPartMetadataElements = readMetada(elementMetadata, bookPartNode);
            List<ParsedPerson> bookPartPersons = readPersons(elementPersons, bookPartNode);
            ParsedMetadata bookPartMetadata = new ParsedMetadata(bookPartPersons, bookPartMetadataElements);
            String lpage = readFirstValue(lpageXpath, bookPartNode);
            String fpage = readFirstValue(fpageXpath, bookPartNode);

            //TODO add Error Message on failure!
            if (StringUtils.isNotBlank(fpage) && StringUtils.isNotBlank(lpage)) {
                result.addBookPart(new BookPart(bookPartMetadata, Integer.parseInt(fpage.trim()), Integer.parseInt(lpage.trim())));
            } else {
                NoMappingPossibleCounter++;
            }
        }
        if (NoMappingPossibleCounter > 0) {
            this.plugin.log("There were "+ NoMappingPossibleCounter +" bookPart elements without lpage or fpage element. The metadata of these Elements could not be mapped to the structure!",LogType.ERROR,false);
        }
        return result;
    }

    private List<MetadataElement> readMetada(List<MetadataMapping> metadaMappings, Object source) {
        List<MetadataElement> metadataElements = new ArrayList<>();
        for (MetadataMapping metadataMapping : metadaMappings) {
            XPathExpression<Object> XpathExpr = metadataMapping.getXpath();
            List<Object> metadataObjects = XpathExpr.evaluate(source);
            List<String> readValues = readValues(metadataObjects);
            for (String readValue : readValues) {
                metadataElements.add(new MetadataElement(metadataMapping.getMets(), readValue));
            }
        }
        return metadataElements;
    }

    private List<ParsedPerson> readPersons(List<PersonMapping> personMappings, Object source) {
        List<ParsedPerson> persons = new ArrayList<>();
        for (PersonMapping personMapping : personMappings) {
            XPathExpression<Object> XpathExpr = personMapping.getXpathNode();
            List<Object> personNodeObjects = XpathExpr.evaluate(source);
            for (Object personNodeObject : personNodeObjects) {
                XPathExpression<Object> firstnameXpathExpr = personMapping.getXpathFirstname();
                String firstname = readFirstValue(personMapping.getXpathFirstname(), personNodeObject);
                String lastname = readFirstValue(personMapping.getXpathLastname(), personNodeObject);
                persons.add(new ParsedPerson(personMapping.getMets(), firstname, lastname));
            }
        }
        return persons;
    }

    private String readFirstValue(XPathExpression<Object> xpathExpression, Object source) {
        List<Object> Objects = xpathExpression.evaluate(source);
        List<String> readValues = readValues(Objects);
        if (readValues.size() >= 1) {
            return readValues.get(0);
        }
        return "";
    }
    
    public static XPathExpression<Object>  compileXpath (String expression) throws IllegalArgumentException, NullPointerException {
        return xpathFactory.compile(expression);
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
                readValues.add((String) val);
            }
        }
        return readValues;
    }
}
