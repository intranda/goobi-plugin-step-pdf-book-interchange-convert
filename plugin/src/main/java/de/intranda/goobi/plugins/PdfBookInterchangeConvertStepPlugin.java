package de.intranda.goobi.plugins;

import java.io.File;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Step;
import org.goobi.beans.Process;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.intranda.goobi.plugins.model.Book;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class PdfBookInterchangeConvertStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_pdf_book_interchange_convert";
    @Getter
    private Step step;
    private Process process;
    private Prefs prefs;
    private String structureTypePdf;
    private String structureTypeBits;
    private List<MetadataMapping> publicationMetadata;
    private List<MetadataMapping> elementMetadata;
    private List<PersonMapping> publicationPersons;
    private List<PersonMapping> elementPersons;
    private String elementFpagePath;
    private String elementLPagePath;
    private String bookPartNodePath;

    private int processId;

    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    private void log(String message, LogType logType) {
        log(message, logType, true);
    }

    public String log(String message, LogType logType, boolean log4j) {

        String logmessage = "PdfBookInterchangeConvert: " + message;
        if (log4j) {
            switch (logType) {
                case INFO:
                    log.info(logmessage + " - ProcessID:" + this.processId);
                    break;
                case ERROR:
                    log.error(logmessage + " - ProcessID:" + this.processId);
                    break;
            }
        }
        if (this.processId > 0) {
            Helper.addMessageToProcessLog(step.getProcessId(), logType, logmessage);
        }
        return logmessage + " - ProcessID:" + this.processId;
    }

    private List<MetadataMapping> getMetadataMapping(String mapping, SubnodeConfiguration myconfig) {
        ArrayList<MetadataMapping> mappings = new ArrayList<>();
        try {
            List<HierarchicalConfiguration> mappingsNodes = myconfig.configurationsAt("//" + mapping + "/metadata");
            for (HierarchicalConfiguration node : mappingsNodes) {
                String xpath = node.getString("@value", null);
                String mets = node.getString("@field", null);
                mappings.add(new MetadataMapping(xpath, mets));
            }
            //TODO catch no nocde exception    
        } catch (NullPointerException ex) {
            log("Invalid" + mapping + " - A mandatory argument is missing. Update the configuration file", LogType.ERROR);
            return null;
        }
        return mappings;
    }

    private List<PersonMapping> getPersonMapping(String mapping, SubnodeConfiguration myconfig) {
        ArrayList<PersonMapping> mappings = new ArrayList<>();
        try {
            List<HierarchicalConfiguration> mappingsNodes = myconfig.configurationsAt("//" + mapping + "/person");
            for (HierarchicalConfiguration node : mappingsNodes) {
                String xpathFirstname = node.getString("@firstname", null);
                String xpathLastname = node.getString("@lastname", null);
                String mets = node.getString("@role", null);
                String xpathNode = node.getString("@xpathNode", null); 
                mappings.add(new PersonMapping(xpathFirstname, xpathLastname, mets, xpathNode));
            }
            //TODO catch no node exception    
        } catch (NullPointerException ex) {
            log("Invalid" + mapping + " - A mandatory argument is missing. Update the configuration file", LogType.ERROR);
            return null;
        }
        return mappings;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.processId = this.step.getProcessId();
        this.process = this.step.getProzess();
        this.prefs = process.getRegelsatz().getPreferences();
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        this.structureTypePdf = myconfig.getString("stuctureTypePdf", null);
        this.structureTypeBits = myconfig.getString("structureTypeBits", null);
        this.publicationMetadata = getMetadataMapping("publicationMapping", myconfig);
        this.publicationPersons = getPersonMapping("publicationMapping", myconfig);
        this.elementFpagePath = myconfig.getString("//elementMapping/fpage/@xpath", null);
        this.elementLPagePath = myconfig.getString("//elementMapping/lpage/@xpath", null);
        if (StringUtils.isBlank(this.elementFpagePath)|| StringUtils.isBlank(this.elementLPagePath)) {
            log("Invalid elementMapping - fpage and lpage elements are missing!", LogType.ERROR);
            
        }
        this.elementMetadata = getMetadataMapping("elementMapping", myconfig);
        this.elementPersons = getPersonMapping("elementMapping", myconfig);
        this.bookPartNodePath = myconfig.getString("//elementMapping/@xpathNode");
        log("Step plugin initialized", LogType.INFO);
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_pdf_book_interchange_convert.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {

        boolean successful = true;
        // find source folder with pdf and xml
        Path sourceFolder = null;
        try {
            sourceFolder = Paths.get(this.process.getSourceDirectory());

            StorageProviderInterface SPI = StorageProvider.getInstance();
            Path masterFolder = Paths.get(process.getImagesOrigDirectory(false));
            if (!SPI.isFileExists(masterFolder)) {
                SPI.createDirectories(masterFolder);
            }

            // TODO check for emtpy list 
            Path pdfFile = FileFilter.getPdfFiles(sourceFolder).get(0);
            Path xmlBitsFile = FileFilter.getXmlFiles(sourceFolder).get(0);
            File folder = new File(masterFolder.toString());
            List<File> imageFiles = new ArrayList<File>();
            imageFiles.addAll(Arrays.asList(folder.listFiles()));
           
            //List<File> imageFiles = PDFConverter.writeImages(pdfFile.toFile(), masterFolder.toFile(), "ghostscript");
            //log("Created " + imageFiles.size() + " TIFF files in " + masterFolder, LogType.DEBUG);
            Fileformat ff = process.readMetadataFile();
            DigitalDocument digitalDocument = ff.getDigitalDocument();
            DocumentManager manager = new DocumentManager(ff, structureTypeBits, imageFiles, this.prefs);
            BitsXmlReader reader = new BitsXmlReader(xmlBitsFile, this.bookPartNodePath);
            Book book = reader.readXml(publicationMetadata, publicationPersons, elementMetadata, elementPersons, elementFpagePath, elementLPagePath);
            ff = manager.mapBookToMets(book);
            //ff = manager.mapBookToMetsWithToc(book);
            DocStruct baseDocStruct = digitalDocument.getLogicalDocStruct();

            if (baseDocStruct.getType().isAnchor()) {
                baseDocStruct = baseDocStruct.getAllChildren().get(0);
            }

            //use pdf-extraction-lib to read pdf and add structure and elements to pdf
            //ff = PDFConverter.writeFileformat(pdfFile.toFile(), imageFiles, ff, this.prefs, 1, baseDocStruct, this.structureTypeBits);

            // find a way to determine  if a toc was present

            // read values from xml
            
            // Book book = reader.readXml(publicationMetadata, publicationPersons, elementMetadata, elementPersons, elementFpagePath, elementLPagePath);
           // process.writeMetadataFile(ff);
        } catch (IllegalArgumentException | IOException | SwapException | DAOException ex) {

        } catch (PreferencesException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } // catch (PDFWriteException e1) {
             // TODO Auto-generated catch block
//             e1.printStackTrace();
//              } 
        catch (ReadException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (MetadataTypeNotAllowedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TypeNotAllowedAsChildException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } 
        
        catch (TypeNotAllowedForParentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } 
//        catch (WriteException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        log("PdfBookInterchangeConvert step plugin executed", LogType.INFO);
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    @Data
    @AllArgsConstructor
    public class MetadataMapping {
        @NonNull
        private String xpath;
        @NonNull
        private String mets;
    }

    @Data
    @AllArgsConstructor
    public class PersonMapping {
        @NonNull
        private String xpathFirstname;
        @NonNull
        private String xpathLastname;
        @NonNull
        private String mets;
        @NonNull
        private String xpathNode;
    }

}
