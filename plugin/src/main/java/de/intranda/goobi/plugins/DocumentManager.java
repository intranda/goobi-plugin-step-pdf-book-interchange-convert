package de.intranda.goobi.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private DocStructType pdfChildType;
    private List<String> overrideMetadaTypes = new ArrayList<String>(Arrays.asList("TitleDocMain"));
    private List<File> imageFiles;
    private PdfBookInterchangeConvertStepPlugin plugin;
    private HashMap<Integer, List<DocstructPageMapping>> pageMapping = new HashMap<Integer, List<DocstructPageMapping>>();

    public DocumentManager(Fileformat fileformat, String childType, String pdfChildType, List<File> imageFiles, Prefs prefs,PdfBookInterchangeConvertStepPlugin plugin ) {
        this.plugin = plugin;
        this.imageFiles = imageFiles;
        Collections.sort(this.imageFiles);
        try {
            this.fileformat = fileformat;
            this.prefs = prefs;
            this.childType = this.prefs.getDocStrctTypeByName(childType);
            this.pdfChildType = this.prefs.getDocStrctTypeByName(pdfChildType);
            this.digitalDocument = this.fileformat.getDigitalDocument();
            this.logical = this.digitalDocument.getLogicalDocStruct();
            this.physical = this.digitalDocument.getPhysicalDocStruct();
        } catch (PreferencesException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public Fileformat mapBookToMetsWithToc(Book book) throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
        addMetadata(logical, book.getMetadata(),true);
        populatePageMapping(childType.getName(), logical, null);
        if (!pageMapping.isEmpty()) {
            //TODO add logic here
            MapToOrCreateElement(logical,book.getBookParts());
        } else {
            plugin.log("No Element with physical pages detected", LogType.ERROR, false);
        }
        return this.fileformat;
    }

    public void populatePageMapping(String childType, DocStruct ds, HashMap<Integer, List<DocstructPageMapping>> PageMapping) {
        List<DocStruct> children = ds.getAllChildrenByTypeAndMetadataType(childType, "*");
        if (children == null)
            return;
        for (DocStruct child : children) {
            List<Reference> refs = child.getAllToReferences("logical_physical");
            List<Integer> pageNumbers = new ArrayList<Integer>();
            for (Reference ref : refs) {
                DocStruct dspage = ref.getTarget();
                List<? extends Metadata> physPage = dspage.getAllMetadataByType(this.prefs.getMetadataTypeByName("physPageNumber"));
                List<Integer> pages = physPage.stream().map(metadata -> Integer.parseInt(metadata.getValue())).collect(Collectors.toList());
                pageNumbers.addAll(pages);
            }
            if (pageNumbers.isEmpty()) {
                continue;
            }
            Collections.sort(pageNumbers);
            DocstructPageMapping dspageMapping = new DocstructPageMapping(child, pageNumbers.get(0), pageNumbers.get(pageNumbers.size() - 1));
            List<DocstructPageMapping> dspageMappings = pageMapping.get(pageNumbers.get(0));
            if (dspageMappings == null) {
                dspageMappings = new ArrayList<DocstructPageMapping>();
                pageMapping.put(pageNumbers.get(0), dspageMappings);
            }
            dspageMappings.add(dspageMapping);
            populatePageMapping(childType, child, PageMapping);
        }
        ds.getAdmId();
        return;
    }

    public Fileformat mapBookToMets(Book book)
            throws MetadataTypeNotAllowedException, TypeNotAllowedAsChildException, TypeNotAllowedForParentException {
        addMetadata(logical, book.getMetadata(),true);
        if (logical.getAllChildrenByTypeAndMetadataType(this.childType.getName(), "*") != null) {

        }
        createElementsAddMetadata(logical, book.getBookParts(),true);
        return this.fileformat;
    }

    private void createElementsAddMetadata(DocStruct ds, List<BookPart> bookParts, boolean rekursive) {
        for (BookPart bookPart : bookParts) {
            DocStruct child = null;
            try {
            child = this.digitalDocument.createDocStruct(this.childType);
            addMetadata(child, bookPart.getMetadata(),false);
            linkImageFiles(child, bookPart.getFirstPage(), bookPart.getLastPage());
            ds.addChild(child);
            } catch (TypeNotAllowedForParentException ex) {
                plugin.log("Type not allowed for parent. Couldn't create DocStruct. Please update the rule set!",LogType.ERROR, false);
                continue;
            } catch (TypeNotAllowedAsChildException e) {
                plugin.log("Type not allowed as child. Couldn't add created DocStruct to parent Element. Please update the rule set!",LogType.ERROR, false);
                continue;
            }
            if (rekursive && bookPart.getBookParts().size() > 0) {
                createElementsAddMetadata(child, bookPart.getBookParts(), true);
            }
        }
    }

    private void MapToOrCreateElement(DocStruct parent,List<BookPart> bookParts) {
        for (BookPart bookPart : bookParts) {
            List<DocstructPageMapping> dsWithSameStartpage = pageMapping.get(bookPart.getFirstPage());
            DocStruct ds = null;
            if (dsWithSameStartpage != null) {
                Optional<DocstructPageMapping> match =
                        dsWithSameStartpage.stream().filter(dspMapping -> dspMapping.getLastPage() == bookPart.getLastPage()).findFirst();
                if (match.isPresent()) {
                    ds = match.get().getDs();
                }
            }
            if (ds == null) {
                plugin.log("Couldn't find matching docstruct for element with start page: " + bookPart.getFirstPage() + " and last page: "
                        + bookPart.getLastPage() + " .New Element will be added to topStruct", LogType.INFO, false);
                ArrayList<BookPart> unmatchedBookPart = new ArrayList<BookPart>();
                unmatchedBookPart.add(bookPart);
                createElementsAddMetadata(parent, unmatchedBookPart, false);
                
            } else {
                addMetadata(ds, bookPart.getMetadata(), true);
            }
            
            if (bookPart.getBookParts().size()>0) {
                MapToOrCreateElement(ds, bookPart.getBookParts());
            }
        }
    }

    private void addMetadata(DocStruct ds, ParsedMetadata metadata, boolean override) {
        for (MetadataElement element : metadata.getMetadata()) {
            try {
                Metadata md = null;
                if (override && overrideMetadaTypes.stream().anyMatch(mets -> mets.equals(element.getMets()))) {
                    md = findExistingElement(ds, element.getMets());
                    if (md == null) {
                        md = new Metadata(prefs.getMetadataTypeByName(element.getMets()));
                    } else {
                        plugin.log("The following "+ element.getMets()+" was overriden: "+md.getValue()+ " with: "+ element.getValue(), LogType.INFO, false );
                    }
                    md.setValue(element.getValue());
                } else {
                    md = new Metadata(prefs.getMetadataTypeByName(element.getMets()));
                    md.setValue(element.getValue());

                    ds.addMetadata(md);

                }
            } catch (MetadataTypeNotAllowedException ex) {
                plugin.log(
                        "Couldn't add metadata of Type " + element.getMets() + " to Structure! Please update the ruleset or the configuration file! ",
                        LogType.ERROR, false);
            }
        }
        for (ParsedPerson person : metadata.getPersons()) {
            try {
            Person p = new Person(prefs.getMetadataTypeByName(person.getMets()));
            p.setFirstname(person.getFirstName());
            p.setLastname(person.getLastName());
           
                ds.addPerson(p);
            } catch (Exception ex) {
                plugin.log(
                        "Couldn't add Person with role " + person.getMets() + " to Structure! Please update the ruleset or the configuration file! ",
                        LogType.ERROR, false);
            }
        }
    }

    private void linkImageFiles(DocStruct ds, int firstPage, int lastPage) {
        StorageProviderInterface storageProvider = StorageProvider.getInstance();
        for (int currentPage = firstPage; currentPage <= lastPage; currentPage++) {
            if (!addPage(ds, this.imageFiles.get(currentPage - 1), currentPage)) {
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
     * tries to find existing MetadataElement of the given element
     * 
     * @param ds DocStruct element that will be searched
     * @param elementType type name of the metadata element
     * @return
     */
    private Metadata findExistingElement(DocStruct ds, String elementType) {
        if (ds.getAllMetadata() != null) {
            for (Metadata md : ds.getAllMetadata()) {
                if (md.getType().getName().equals(elementType)) {
                    return md;
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
