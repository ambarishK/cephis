package org.contentmine.image.ocr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * manages the pipeline for running Tesseract and creating HTML and SVG Documents.
 * hides the fragmented nature from users.
 * 
 * @author pm286
 *
 */
public class OCRProcessor {
	private static final Logger LOG = Logger.getLogger(OCRProcessor.class);
	static {
		LOG.setLevel(Level.DEBUG);
	}
	
	private ImageToHOCRConverter imageToHOCRConverter;
	
	public OCRProcessor() {
		
	}

	/** contains 2 steps.
	 * convert image to HOCR
	 * return a HOCR processor 
	 * 
	 * @param imageFile
	 * @param hocrOutfile
	 * @return the hocrProcessor which can be further interrogated.
	 * 
	 * @throws IOException
	 */
	public HOCRReader createHOCRReaderAndProcess(File imageFile, File hocrOutfile) throws IOException {
		ImageToHOCRConverter imageToHOCRConverter = this.getOrCreateImageToHOCRConverter();
		File htmlFile = imageToHOCRConverter.convertImageToHOCR(imageFile, hocrOutfile);
		if (htmlFile == null || !htmlFile.exists()) {
			HOCRTest.LOG.error("cannot run tesseract");
			return null;
		}
		HOCRReader hocrReader = new HOCRReader();
		hocrReader.readHOCR(new FileInputStream(htmlFile));
		return hocrReader;
	}

	/** gets processor and ensures default tries for OCR/Tesseract.
	 * 
	 * @return
	 */
	public ImageToHOCRConverter getOrCreateImageToHOCRConverter() {
		if (imageToHOCRConverter == null) {
			imageToHOCRConverter = new ImageToHOCRConverter();
			imageToHOCRConverter.setTryCount(ImageToHOCRConverter.DEFAULT_RETRIES_FOR_TESSERACT_EXIT);
		}
		return imageToHOCRConverter;
	}
	


}