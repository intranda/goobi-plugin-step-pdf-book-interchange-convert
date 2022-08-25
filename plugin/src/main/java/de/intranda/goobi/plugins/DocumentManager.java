package de.intranda.goobi.plugins;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.production.enums.LogType;

import de.intranda.goobi.plugins.model.Book;
import de.intranda.goobi.plugins.model.BookPart;
import de.intranda.goobi.plugins.model.MetadataElement;
import de.intranda.goobi.plugins.model.ParsedMetadata;
import de.intranda.goobi.plugins.model.ParsedPerson;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;

public class DocumentManager {
    private Fileformat fileformat;
    private DigitalDocument digitalDocument;
    private DocStruct logical;
    private DocStruct physical;
    private Process process;
    private Prefs prefs;
    private DocStructType childType;
    private List<File> imageFiles;
    private PdfBookInterchangeConvertStepPlugin plugin;


    public DocumentManager(Fileformat fileformat, String childType, List<File> imageFiles, Prefs prefs) {

       
        this.imageFiles = imageFiles;
        Collections.sort(this.imageFiles);
        try {
            this.fileformat = fileformat;
            this.prefs = prefs;
            this.childType = this.prefs.getDocStrctTypeByName(childType);
            this.digitalDocument = this.fileformat.getDigitalDocument();
            this.logical = this.digitalDocument.getLogicalDocStruct();
            this.physical = this.digitalDocument.getPhysicalDocStruct();
            //Metadata imagePath = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
            //imagePath.setValue(process.getImagesOrigDirectory(false));
            //this.physical.addMetadata(imagePath);
        } catch ( PreferencesException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public Fileformat mapBookToMetsWithToc (Book book) throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
       // addMetadata(logical, book.getMetadata());
        //logical.getAllChildrenByTypeAndMetadataType(this.childType.getName(), "*");
        createPageMapping(childType.getName(), logical, null);
        return null;
    }
    
    public HashMap<Integer, List<DocstructPageMapping>> createPageMapping (String childType, DocStruct ds, HashMap<Integer, List<DocstructPageMapping>> PageMapping) {
       List<DocStruct> children =  logical.getAllChildrenByTypeAndMetadataType(childType, "*");
       for (DocStruct child : children) {
           List<Reference> refs = child.getAllToReferences("logical_physical");
           //TODO add logic to read PageNumbers
           //createPageMapping(childType, child, PageMapping);
       }
       return null;
    }
    
    public Fileformat mapBookToMets(Book book) throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException, TypeNotAllowedForParentException {
        addMetadata(logical, book.getMetadata());
        addMetadataAndElements(logical, book.getBookParts());
        return this.fileformat;
    }

    public void addMetadataAndElements(DocStruct ds, List<BookPart> bookParts)
            throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException, TypeNotAllowedForParentException {
        for (BookPart bookPart : bookParts) {
            DocStruct child = this.digitalDocument.createDocStruct(this.childType);
            addMetadata(child, bookPart.getMetadata());
            linkImageFiles(child, bookPart.getFirstPage(), bookPart.getLastPage());
            ds.addChild(child);
            if (bookPart.getBookParts().size() > 0) {
                addMetadataAndElements(child, bookPart.getBookParts());
            }
        }
    }

    public void addMetadata(DocStruct ds, ParsedMetadata metadata) throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
        for (MetadataElement element : metadata.getMetadata()) {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(element.getMets()));
            md.setValue(element.getValue());
            ds.addMetadata(md);
        }

        for (ParsedPerson person : metadata.getPersons()) {
            Person p = new Person(prefs.getMetadataTypeByName(person.getMets()));
            p.setFirstname(person.getFirstName());
            p.setLastname(person.getLastName());
            ds.addPerson(p);
        }
    }

    private void linkImageFiles(DocStruct ds, int firstPage, int lastPage) {
        StorageProviderInterface storageProvider = StorageProvider.getInstance();
        for (int currentPage = firstPage; currentPage <= lastPage; currentPage++) {
            if (!addPage(ds, this.imageFiles.get(currentPage-1), currentPage)) {
                plugin.log("Couldn't add Page to Structure", LogType.ERROR, false);
            }
        }
    }

    /**
     * adds page to the physical docstruct and links it to the logical docstruct-element
     * 
     * @param ds
     * @param dd
     * @param imageFile
     * @return true if successful
     */
    private boolean addPage(DocStruct ds, File imageFile, int PageCount) {
        try {
            DocStructType newPage = prefs.getDocStrctTypeByName("page");
            DocStruct dsPage = digitalDocument.createDocStruct(newPage);
            // physical page no
            physical.addChild(dsPage);
            MetadataType mdt = prefs.getMetadataTypeByName("physPageNumber");
            Metadata mdTemp = new Metadata(mdt);
            mdTemp.setValue(String.valueOf(PageCount));
            dsPage.addMetadata(mdTemp);

            // logical page no
            mdt = prefs.getMetadataTypeByName("logicalPageNumber");
            mdTemp = new Metadata(mdt);

            mdTemp.setValue("uncounted");

            dsPage.addMetadata(mdTemp);
            ds.addReferenceTo(dsPage, "logical_physical");

            // image name
            ContentFile cf = new ContentFile();

            cf.setLocation("file://" + imageFile.getName());

            dsPage.addContentFile(cf);
            return true;
        } catch (TypeNotAllowedAsChildException | TypeNotAllowedForParentException e) {
            plugin.log("Error creating page - type not allowed as child/for parent", LogType.ERROR, false);
            return false;
        } catch (MetadataTypeNotAllowedException e) {
            plugin.log("Error creating page - Metadata type not allowed", LogType.ERROR, false);
            return false;
        }
    }
    
    /**
     * Returns true if the DocStruct-element allows to append this metadata type
     * 
     * @param ds
     * @param elementType
     * @return
     */
    private boolean isAllowedElement(DocStruct ds, String elementType) {
        for (MetadataType metadataType : ds.getType().getAllMetadataTypes()) {
            if (metadataType.getName().equals(elementType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * tries to find existing MetadataElement of the given Element
     * 
     * @param logical DocStruct element that will be searched
     * @param elementType type name of the URN
     * @return
     */
    private String findExistingElement(DocStruct logical, String elementType) {
        if (logical.getAllMetadata() != null) {
            for (Metadata md : logical.getAllMetadata()) {
                if (md.getType().getName().equals(elementType)) {
                    return md.getValue();
                }
            }
        }
        return null;
    }
    
    @Data
    @AllArgsConstructor
    public class DocstructPageMapping {
        private DocStruct ds;
        @NonNull
        private int firstPage;
        @NonNull
        private int lastPage;
    }
}
