package org.contentmine.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;

import org.apache.log4j.Logger;
import org.contentmine.eucl.euclid.Int2Range;
import org.contentmine.eucl.euclid.IntArray;
import org.contentmine.eucl.euclid.IntMatrix;
import org.contentmine.eucl.euclid.IntRange;
import org.contentmine.eucl.euclid.Real2;
import org.contentmine.eucl.euclid.RealMatrix;
import org.contentmine.graphics.svg.SVGG;
import org.contentmine.graphics.svg.SVGRect;
import org.contentmine.graphics.svg.SVGSVG;
import org.contentmine.image.processing.HilditchThinning;
import org.contentmine.image.processing.Thinning;
import org.contentmine.image.processing.ZhangSuenThinning;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.struct.image.ImageUInt8;

public class ImageUtil {
	private final static Logger LOG = Logger.getLogger(ImageUtil.class);

	public static final IntArray SMEAR_ARRAY = new IntArray(new int[]{1,3,6,10,20,10,6,3,1});
	public static final IntArray DOUBLE_ARRAY = new IntArray(new int[]{1,2,1});
	public static final IntArray IDENT_ARRAY = new IntArray(new int[]{1});
	public static final IntArray EDGE_ARRAY = new IntArray(new int[]{1,0,-1});
	public static final IntArray SHARPEN_ARRAY = new IntArray(new int[]{-10, 20, -10});

	public static int RED = 0;
	public static int GREEN = 1;
	public static int BLUE = 2;
	public static int[] RGB = {RED, GREEN, BLUE};

	public static BufferedImage zhangSuenThin(BufferedImage image) {
		Thinning thinningService = new ZhangSuenThinning(image);
		thinningService.doThinning();
		image = thinningService.getThinnedImage();
		return image;
	}

	public static BufferedImage hilditchThin(BufferedImage image) {
		Thinning thinningService = new HilditchThinning(image);
		thinningService.doThinning();
		image = thinningService.getThinnedImage();
		return image;
	}

	public static BufferedImage thin(BufferedImage image, Thinning thinning) {
		thinning.createBinary(image);
		thinning.doThinning();
		image = thinning.getThinnedImage();
		return image;
	}

	
	public static BufferedImage boofCVBinarization(BufferedImage image, int threshold) {
		ImageUInt8 input = ConvertBufferedImage.convertFrom(image,(ImageUInt8)null);
		ImageUInt8 binary = new ImageUInt8(input.getWidth(), input.getHeight());
		ThresholdImageOps.threshold(input, binary, threshold, false);
		BufferedImage outputImage = VisualizeBinaryData.renderBinary(binary,null);
		return outputImage;
	}


	/** extracts a subimage translated to 0,0.
	 * 
	 * clip to bounding box inclusive? or edge of image
	 * 
	 * @param image 
	 * @return null if clip is outside size of image
	 */
	public static BufferedImage clipSubImage(BufferedImage image, Int2Range boundingBox) {
		BufferedImage subImage = null;
		IntRange xRange = boundingBox.getXRange();
		IntRange yRange = boundingBox.getYRange();
		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();
		int xMin = xRange.getMin();
		int yMin = yRange.getMin();
		int clipWidth = xRange.getRange();
		clipWidth = Math.min(clipWidth, imageWidth - xMin);
		int clipHeight = yRange.getRange();
		clipHeight = Math.min(clipHeight, imageHeight - yMin);
		if (clipWidth > 0 && clipHeight > 0) {
			subImage = new BufferedImage(clipWidth, clipHeight, image.getType());
			for (int i = 0; i < clipWidth; i++) {
				int xx = i + xMin;
				for (int j = 0; j < clipHeight; j++) {
					int yy = j + yMin;
					int rgb = image.getRGB(xx, yy);
					subImage.setRGB(i, j,  rgb);
				}
			}
		}
		return subImage;
	}

	public static double correlateGray(BufferedImage image,
			BufferedImage image2, String title) {
		double cor = 0.0;
		int xrange = Math.min(image.getWidth(), image2.getWidth());
		int yrange = Math.min(image.getHeight(), image2.getHeight());
		SVGG g = new SVGG();
		double total = 0;
		double sum = 0;
		Real2 centre = new Real2(0.0, 0.0);
		Real2 centre2 = new Real2(0.0, 0.0);
		double sumGray = 0.0;
		double sumGray2 = 0.0;
		for (int i = 0; i < xrange; i++) {
			for (int j = 0; j < yrange; j++) {
				int gray = getGray(image, i,j);
				int gray2 = getGray(image2, i,j);
				if (gray < 0 || gray2 < 0) {
					throw new RuntimeException("bad gray value "+Integer.toHexString(gray)+" "+Integer.toHexString(gray2));
				}
				int diff = Math.abs(gray - gray2);
				int max = Math.max(gray, gray2);
				total += max;
				int score = max - 2 * diff;
				sum += score;
				SVGRect rect = new SVGRect((double)i, (double)j, 1.0, 1.0);
				Color color = new Color(255-gray, 0, 255-gray2);
				String colorS = "#"+Integer.toHexString(color.getRGB()).substring(2);
				rect.setFill(colorS);
				rect.setStroke("none");
				g.appendChild(rect);
				centre.plusEquals(new Real2(i * gray, j * gray));
				sumGray += gray;
				centre2.plusEquals(new Real2(i * gray2, j * gray2));
				sumGray2 += gray2;
			}
		}
//		double scale = 1./(double)(xrange * yrange);
		centre.multiplyEquals(1./sumGray);
		centre2.multiplyEquals(1./sumGray2);
		if (title != null) {
			LOG.trace(centre.format(1)+" >> "+centre2.format(1)+" "+centre.subtract(centre2).format(1));
			File file = new File("target/corrPixels/"+title+".svg");
			file.getParentFile().mkdirs();
			SVGSVG.wrapAndWriteAsSVG(g, file);
		}
		cor = sum / total;
		return cor;
	}

	/** gets gray value.
	 * 
	 * this is messier than I thought - need a formal library.
	 * 
	 * range 0-> ff
	 * @param rgb assumed to be grayscale (r==g==b)
	 * @return gray or -1 if not a gray color
	 */
	public static int getGray(int rgb) {
		int gray = -1; // no color
		if (rgb == 0) {
			gray = 255; // assume transparent?
		} else {
			int alpha = getAlpha(rgb);
			int r = getRed(rgb);
			int g = getGreen(rgb);
			int b = getBlue(rgb);
			if (r == 0 && g == 0 && b == 0) {
				gray = 255 - alpha; // black seems to be #ff000000
			} else if (r == g && g == b) {
				gray = r; // omit transparent 
			} else {
				throw new RuntimeException("unprocessable value: "+Integer.toHexString(rgb));
			}
		}
		if (gray == -1) {
			throw new RuntimeException("unprocessed value: "+Integer.toHexString(rgb));
		}
		if (gray != 0) {
//			System.out.print(gray+" ");
		}
		return gray;
	}

	public static int getBlue(int rgb) {
		int blue = rgb;
		return (blue & 0xff) ;
	}

	public static int getGreen(int rgb) {
		int green = rgb >> 8;
		return (green & 0xff);
	}

	public static int getRed(int rgb) {
		int red = rgb >> 16;
		return (red & 0xff) ;
	}

	private static int getAlpha(int rgb) {
		int alpha = rgb >> 24;
		return (alpha & 0xff) ;
	}
	
	public static int getGray(BufferedImage image, int x, int y) {
		return getGray(image.getRGB(x, y));
	}

	/** extracts matrix from grayImage.
	 * 
	 * @param image
	 * @return matrix (null if not a gray image)
	 */
	public static IntMatrix getGrayMatrix(BufferedImage image) {
		int cols = image.getWidth();
		int rows = image.getHeight();
		IntMatrix matrix = new IntMatrix(rows, cols, 0);
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				int gray = ImageUtil.getGray(image, j, i);
				matrix.setElementAt(i,  j, gray);
			}
		}
		return matrix;
	}

	/** creates image from RealMatrix..
	 * 
	 * @param matrix values must be 0<=val<=255
	 * @return images values are clipped to 0<v<255 without warning 
	 */
	public static BufferedImage putGrayMatrix(IntMatrix matrix) {
		int cols = matrix.getCols();
		int rows = matrix.getRows();
		LOG.trace("rc "+rows+" "+cols);
		if (rows <= 0 || cols <= 0) return null;
		BufferedImage image = new BufferedImage(cols, rows, BufferedImage.TYPE_BYTE_GRAY);
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				LOG.trace(i+" "+j);
				int gray = (int) matrix.elementAt(i, j);
				if (gray < 0) {
					gray = 0;
				} else if (gray > 255) {
					gray = 255;
				}
				int grayColor = 256*256*gray + 256 * gray + gray; 
				image.setRGB(j, i, grayColor);
			}
		}
		return image;
	}

	public static BufferedImage shiftImage(BufferedImage image, double deltax, double deltay) {
		IntMatrix matrix = ImageUtil.getGrayMatrix(image);
		RealMatrix realMatrix = new RealMatrix(matrix);
		RealMatrix shiftedMatrix = realMatrix.createMatrixWithOriginShifted(deltax, deltay);
		IntMatrix matrix0 = new IntMatrix(shiftedMatrix);
		BufferedImage shiftedImage = ImageUtil.putGrayMatrix(matrix0);
		return shiftedImage;
	}

	/**
	@deprecated use Imgscalr instead {@link ImageUtil#scaleImage(int, int, BufferedImage)}
	*/
	@Deprecated
	public static BufferedImage scaleAndInterpolate(BufferedImage image,
			int newRows, int newCols) {
		RealMatrix matrix = new RealMatrix(ImageUtil.getGrayMatrix(image));
		RealMatrix shiftedMatrix = matrix.scaleAndInterpolate(newRows, newCols);
		BufferedImage shiftedImage = ImageUtil.putGrayMatrix(new IntMatrix(shiftedMatrix));
		return shiftedImage;
	}

//	/** makes parent directly if not exists.
//	 * 
//	 * selects type from extension; chooses ".png" if none 
//	 * @param image
//	 * @param file
//	 */
//	@Deprecated // use org.contentmine.image.ImageUtil
//	private static void writeImageQuietlyX(BufferedImage image, File file) {
//		if (image == null) {
//			throw new RuntimeException("Cannot write null image: "+file);
//		}
//		try {
//			// DON'T EDIT!
//			String type = FilenameUtils.getExtension(file.getName());
//			if (type == null || type.equals("")) {
//				type ="png";
//			}
//			file.getParentFile().mkdirs();
//			ImageIO.write(image, type, new FileOutputStream(file));
//		} catch (Exception e) {
//			throw new RuntimeException("cannot write image "+file, e);
//		}
//	}
	/** uses Imgscalr to scale.
	 * 
	 * @param width
	 * @param height
	 * @param genImage
	 * @return
	 */
	public static BufferedImage scaleImage(int width, int height,
			BufferedImage genImage) {
		BufferedImage scaledGenImage = Scalr.resize(genImage, Method.ULTRA_QUALITY, Mode.FIT_EXACT, width,
		        height);
		return scaledGenImage;
	}

//	/** writes file making dirs if required
//	 * 
//	 * @param image creates filetype from filename suffix
//	 * @param filename
//	 * @return
//	 */
//	public static File writeImageQuietlyXX(BufferedImage image, String filename) {
//		File file = new File(filename);
//		writeImageQuietlyX(image, file);
//		return file;
//	}

	public static BufferedImage addBorders(BufferedImage image0, int xmargin, int ymargin, int color) {
		if (image0 == null) {
			return null;
		}
		BufferedImage image = new BufferedImage(image0.getWidth() + 2*xmargin,  image0.getHeight()+2*ymargin, image0.getType());
		// set to colour
		for (int i = 0; i < image0.getWidth() + 2 * xmargin; i++) {
			for (int j = 0; j < image0.getHeight() + 2 * ymargin; j++) {
				image.setRGB(i, j, color);
			}
		}
		// copy
		for (int i = 0; i < image0.getWidth(); i++) {
			for (int j = 0; j < image0.getHeight(); j++) {
				image.setRGB(i + xmargin, j + ymargin, image0.getRGB(i, j));
			}
		}
		return image;
	}

	/** average colours locally.
	 * 
	 * 
	 * @param image
	 * @param 
 	 * @return new BufferedImage
	 */
	public static BufferedImage averageImageColors(BufferedImage image) {

		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage image1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int i = 1; i < width - 1; i++) {
			for (int j = 1; j < height - 1; j++) {
				int rgbij = image.getRGB(i, j);
				if (!isWhite(ImageUtil.getRGBChannels(rgbij), 200)) {
					int[] rgbx =  averageOverNeighbours(image, i, j);
					rgbij = ImageUtil.setRgb(rgbx[0], rgbx[1], rgbx[2]);
				}
				image1.setRGB(i, j, rgbij); 
			}
		}
		return image1;
	}

	private static int[] averageOverNeighbours(BufferedImage image, int i, int j) {
		int count = 0;
		int[] rgbs = new int[3];
		for (int k = -1; k <= 1; k++) {
			for (int l = -1; l <= 1; l++) {
				int rgb = image.getRGB(i + k, j + l);
				int[] rgbx = ImageUtil.getRGBChannels(rgb);
				if (!isWhite(rgbx, 250)) {
					for (int m = 0; m < 3; m++) {
						rgbs[m] += rgbx[m];
					}
					count++;
				}
			}
		}
		if (count != 0) {
			for (int m = 0; m < 3; m++) {
				rgbs[m] /= count;
			}
		} else {
			rgbs = ImageUtil.getRGBChannels(image.getRGB(i, j));
		}
		return rgbs;
	}


	private static boolean isWhite(int[] rgbx, int thresh) {
		return rgbx[0] > thresh && rgbx[1] > thresh && rgbx[2] > thresh;
	}

	/** flatten colours in image.
	 * 
	 * uses ImageUtil.flattenPixel
	 * 
	 * creates nvalues of single colour with min 0 and max 255. thus  
	 * 
	 * @param image
	 * @param nvalues number of discrete (integer) values of r or g or b. 
 	 *        currently 2, 4, 8, 16, 32, 64, 128 (maybe alter this later)
 	 * @return new BufferedImage
	 */
	public static BufferedImage flattenImage(BufferedImage image, int nvalues) {

		if (nvalues != 2 && nvalues != 4 && nvalues != 8 && nvalues != 16 &&
		    nvalues != 32 && nvalues != 64 && nvalues != 128) {
			throw new RuntimeException("Bad value of nvalues, should be power of 2 within 2 - 128");
		}
		int delta = 256 / nvalues;
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage image1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				image1.setRGB(i, j, 0);
				flattenPixel(image, i, j, delta, image1);
			}
		}
		return image1;
	}

	/** flattens pixel to range of values.
	 * 
	 * @param image
	 * @param i
	 * @param j
	 * @param delta distance between values (power of 2)
	 */
	public static void flattenPixel(BufferedImage image, int i, int j, int delta, BufferedImage image1) {
		int rgb = image.getRGB(i, j);
		int r = getRed(rgb);
		int g = getGreen(rgb);
		int b = getBlue(rgb);
		
		r = flattenChannel(r, delta);
		g = flattenChannel(g, delta);
		b = flattenChannel(b, delta);
		
		int col = (r << 16) | (g << 8) | b;
		image1.setRGB(i, j, col);
	}

	/**
	 * 
	 * @param r r/g/b channel (0-255)
	 * @param delta distance between allowed values (power of 2)
	 * @return nearest fencepost value (0 - 255) at intervals of delta
	 */
	public static int flattenChannel(int r, int delta) {
		int rr = r + delta/2; // round to nearest fencepost
		rr = (rr / delta) * delta;
		return rr == 0 ? 0 : rr - 1;
	}

	public static int setRgb(int red, int green, int blue) {
		int rgb = red * 256 * 256 + green * 256 + blue;
//		LOG.debug(red+", "+green+", "+blue+"//"+Integer.toHexString(rgb)+"/"+ImageUtil.debugRGB(rgb));
		return rgb;
	}

	/** flip to (255-r), (255.g), (255,b)
	 * 
	 * @param rgb
	 * @return
	 */
	public static int invertRgb(int rgb) {
		int flip = setRgb(255 - getRed(rgb), 255 - getGreen(rgb), 255 - getBlue(rgb));
		return flip;
	}
	
	public static int[] getRGBChannels(int rgb) {
		int red = getRed(rgb);
		int green = getGreen(rgb);
		int blue = getBlue(rgb);
		int[] channels = new int[] {red, green, blue};
		return channels;
	}

	public static String debugRGB(int rgb) {
		int[] channels = ImageUtil.getRGBChannels(rgb);
		return "r="+channels[RED]+",g="+channels[GREEN]+",b="+channels[BLUE];
	}
	
	/** deep copy image.
	 * 
	 * thanks to https://stackoverflow.com/questions/3514158/how-do-you-clone-a-bufferedimage
	 * 
	 * @param bi input image
	 * @return deep copy
	 */
	public static BufferedImage deepCopy(BufferedImage bi) {
		 ColorModel cm = bi.getColorModel();
		 boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		 WritableRaster raster = bi.copyData(null);
		 return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}

}
