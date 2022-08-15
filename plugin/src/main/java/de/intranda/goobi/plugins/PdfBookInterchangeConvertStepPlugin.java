package de.intranda.goobi.plugins;



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
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Step;
import org.goobi.beans.Process;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class PdfBookInterchangeConvertStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_pdf_book_interchange_convert";
    @Getter
    private Step step;
    private Process process;
    private String structureTypePdf;
    private String structureTypeBits;
    private List<MetadataMapping> publicationMetadata;
    private List<MetadataMapping> elementMetadata;
    private List<PersonMapping> publicationPersons;
    private List<PersonMapping> elementPersons;

    private int processId;

    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    private void log(String message, LogType logType) {

        String logmessage = "PdfBookInterchangeConvert: " + message;
        switch (logType) {
            case INFO:
                log.info(logmessage + " - ProcessID:" + this.processId);
                break;
            case ERROR:
                log.error(logmessage + " - ProcessID:" + this.processId);
                break;
        }
        if (this.processId > 0) {
            Helper.addMessageToProcessLog(step.getProcessId(), logType, logmessage);
        }
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
                mappings.add(new PersonMapping(xpathFirstname, xpathLastname, mets));
            }
            //TODO catch no node exception    
        } catch (NullPointerException ex) {
            log("Invalid" + mapping + " - A mandatory argument is missing. Update the configuration file", LogType.ERROR);
        }
        return mappings;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.processId = this.step.getProcessId();
        this.process = this.step.getProzess();
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        this.structureTypePdf = myconfig.getString("stuctureTypePdf", null);
        this.structureTypeBits = myconfig.getString("structureTypeBits", null);
        this.publicationMetadata = getMetadataMapping("publicationMapping", myconfig);
        this.publicationPersons = getPersonMapping("publicationMapping", myconfig);
        this.elementMetadata = getMetadataMapping("elementMapping", myconfig);
        this.elementPersons = getPersonMapping("elementMapping", myconfig);
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

    public String readValue(Document doc, String xpathString) {
        XPathExpression<Object> xpath = XPathFactory.instance().compile(xpathString);
        Object value = xpath.evaluateFirst(doc);
        
        if (value instanceof Element) {
            value = ((Element) value).getTextTrim();
        } else if (value instanceof Attribute) {
            value = ((Attribute) value).getValue();
        } else if (value instanceof Text) {
            value = ((Text) value).getText();
        } else if (value != null && !(value instanceof String)) {
            value = value.toString();
        }

        if (value instanceof String) {
            return (String) value;
        } else {
            throw new IllegalArgumentException("Could not read value for Expression: " + xpath);
        }
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
   
        // read table of contents
        // read values from xml
            //open xml file
        SAXBuilder jdomBuilder = new SAXBuilder();
        jdomBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        jdomBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        jdomBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document jdomDocument;
        Path sourceFolder = null;
        try {
            sourceFolder = Paths.get(this.process.getSourceDirectory());
        } catch (IOException | SwapException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (sourceFolder != null && Files.exists(sourceFolder)) {
            try {
                jdomDocument = jdomBuilder.build(sourceFolder.toString());
            } catch (JDOMException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        // create pages and images

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
    }

}
