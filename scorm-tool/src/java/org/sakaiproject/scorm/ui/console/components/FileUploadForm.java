package org.sakaiproject.scorm.ui.console.components;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.lang.Bytes;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.scorm.ui.AbsoluteUrl;

public class FileUploadForm extends Form {

	private static final long serialVersionUID = 1L;

	private static Log log = LogFactory.getLog(FileUploadForm.class);		
	private static final String FILE_UPLOAD_MAX_SIZE_CONFIG_KEY = "content.upload.max";	
	private static final String CONTENT_TYPE_APPLICATION_ZIP = "application/zip";
	
	@SpringBean
	ServerConfigurationService configurationService;
	
	private FileUploadField fileUploadField;
	
	public FileUploadForm(String id) {
		super(id);
		
		IModel model = new CompoundPropertyModel(this);
		this.setModel(model);
		
		// We need to establish the largest file allowed to be uploaded
		setMaxSize(Bytes.megabytes(findMaxFileUploadSize()));
		
		setMultiPart(true);
		
		//add(newResourceLabel("fileToUploadLabel", this));
	}

	public void addFields(MarkupContainer container) {
		container.addOrReplace(fileUploadField = new FileUploadField("fileInput"));
		container.addOrReplace(new Label("fileToUploadLabel", "File Location"));
	}
	
	public boolean isFileAvailable() {
		if (fileUploadField != null) {
			FileUpload upload = fileUploadField.getFileUpload();
	        if (upload != null)
	        	return upload.getSize() != 0;
		}
		
		return false;
	}
	
	public File doUpload() {
		if (fileUploadField != null) {
			final FileUpload upload = fileUploadField.getFileUpload();
	        if (upload != null)
	        {
	            // Create a new file
	            File newFile = new File("/home/jrenfro/junk", upload.getClientFileName());
	
	            // Check new file, delete if it allready existed
	            //checkFileExists(newFile);
	            try
	            {
	                // Save to new file
	                newFile.createNewFile();
	                upload.writeTo(newFile);
	
	                return newFile;
	            }
	            catch (Exception e)
	            {
	                throw new IllegalStateException("Unable to write file");
	            }
	        }
		}
		notify("noFile");
		return null;
	}
	
	
	public final void notify(String key) {
		String message = null;
		try {
			message = getLocalizer().getString(key, this);
		} catch (Exception e) {
			log.warn("Unable to find the message for key: " + key);
		}
		
		if (message == null)
			message = key;
		
		Session.get().getFeedbackMessages().warn(this, message);
	}
	
	protected Label newResourceLabel(String id, Component component) {
		return new Label(id, new StringResourceModel(id, component, null));
	}
	
	
	public void exit(String url) {
		if (null != url) {
			AbsoluteUrl absUrl = new AbsoluteUrl(getRequest(), url, true, false);
			String fullUrl = absUrl.toString();
			getRequestCycle().setRequestTarget(new RedirectRequestTarget(absUrl.toString()));
		}
	}
	
	private File getDirectory() {		
		return (File)((WebRequest)getRequest()).getHttpServletRequest().getSession().getServletContext().getAttribute("javax.servlet.context.tempdir");
	}
	
	public File getFile(FileItem fileItem) {
		if (null == fileItem || fileItem.getSize() <= 0) {
			notify("noFile");
			return null;
		}
		

		if (!CONTENT_TYPE_APPLICATION_ZIP.equals(fileItem.getContentType())) {
			notify("wrongContentType");
		}


		String filename = fileItem.getName();
        
		File file = new File(getDirectory(), filename);

		System.out.println("FILE IS: " + file.getAbsolutePath());
		
		byte[] bytes = fileItem.get();
		
		InputStream in = null;
		FileOutputStream out = null;
				
		try {
			if (null == bytes) {
				in = fileItem.getInputStream();
			} else {
				in = new ByteArrayInputStream(bytes);
			}
		
			out = new FileOutputStream(file);
			
			byte[] buffer = new byte[1024];
			int length;
			
			while ((length = in.read(buffer)) > 0) {  
    			out.write(buffer, 0, length);
			}
			
			
		} catch (IOException ioe) {
			log.error("Caught an io exception retrieving the uploaded content package!", ioe);
		} finally {
			if (null != out)
				try {
					out.close();
				} catch (IOException nioe) {
					log.info("Caught an io exception closing the output stream!", nioe);
				}
		}
		
		
		return file;
	}
		
	/*public void setMultiPart(boolean mtiPart)
	{
		super.setMultiPart(false);
	}*/
	
	private int findMaxFileUploadSize() {
		String maxSize = null;
		int megaBytes = 1;
		try {
			maxSize = configurationService.getString(FILE_UPLOAD_MAX_SIZE_CONFIG_KEY, "1");
			if (null == maxSize)
				log.warn("The sakai property '" + FILE_UPLOAD_MAX_SIZE_CONFIG_KEY + "' is not set!");
			else
				megaBytes = Integer.parseInt(maxSize);
		} catch(NumberFormatException nfe) {
			log.error("Failed to parse " + maxSize + " as an integer ", nfe);
		}
		
		return megaBytes;
	}


	/*public FileUpload getFileInput() {
		return fileInput;
	}


	public void setFileInput(FileUpload fileInput) {
		this.fileInput = fileInput;
	}*/


	/*public FileUploadField getFileUploadField() {
		return fileUploadField;
	}


	public void setFileUploadField(FileUploadField fileUploadField) {
		this.fileUploadField = fileUploadField;
	}*/
	
	/*public FileItem getFileItem() {
		return (FileItem)((WebRequest)getRequest()).getHttpServletRequest().getAttribute("fileInput");
	}*/

}