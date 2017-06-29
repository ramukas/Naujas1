import java.lang.Character;

import java.util.*;
import java.io.*;
import iotools.*;

import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.util.PDFTextStripper;
import java.nio.CharBuffer;

import org.jpedal.PdfDecoder;
import org.jpedal.exception.PdfException;
import org.jpedal.exception.PdfSecurityException;
import org.jpedal.grouping.PdfGroupingAlgorithms;
import org.jpedal.objects.PdfPageData;
import org.jpedal.utils.LogWriter;
import org.jpedal.utils.Strip;

public class Pdf2text
{
// --- constructor ---
public Pdf2text() {}
// -------------------

/*

public static void main(String[] args) throws Exception
{
  String file_name = "picrender.pdf";

  Entrez entrez = new Entrez();
  entrez.PMC_fetchPDF("1075854", file_name);

  FileWriter file_out = fileOut("from_pdf.txt"); 
             file_out.write(getTextFromPDF(file_name).toString());
             file_out.close();
}

*/

// ----------------------------------------------------------
// --- extract text from a PDF file combining pdfbox & jpedal
// ----------------------------------------------------------
public static StringBuffer getTextFromPDF(String file_name) throws IOException
{
  int ref; // --- start point of citations


  // ----------------------------------------------------------
  // --------------------- P D F B O X ------------------------
  // ----------------------------------------------------------
  // --- read text from PDF (using pdfbox)
  StringBuffer txt = extractTextFromPDF(file_name);
  if ( ( ref = indexOfReferences(txt.toString()) ) > 0)
  {
    // --- delete the citations
    txt.delete(ref, txt.length());
    txt.append(" ");
  }
  // ----------------------------------------------------------


  // ----------------------------------------------------------
  // --------------------- J P E D A L ------------------------
  // ----------------------------------------------------------
  // --- read words from PDF (using jpedal)
  String words = fixHyphenation(extractWordsFromPDF(file_name));
  if ( ( ref = indexOfReferences(words) ) > 0)
  {
    // --- delete the words from the citations list
    words = words.substring(0, ref);
  }
  // ----------------------------------------------------------


  // ----------------------------------------------------------
  // -------- fix the ZONING problem in the OCR text ----------
  // ----------------------------------------------------------
  // --- segment the words in the PDFBOX-produced TEXT
  //     using JPEDAL-produced WORDS
  // ----------------------------------------------------------
  StringBuffer txt_segmented = new StringBuffer();
  int i = 0;                // --- current position in the text
  int tlen = txt.length();
  int wlen = words.length();
  int wb = 0, we;           // --- word boundaries

  while (wb < wlen)          // --- for each word
  {
    // --- extract word
    we = words.indexOf(" ", wb);
    String word = words.substring(wb, we);

    // --- copy word to text
    txt_segmented.append(word);

    i = txt.indexOf(word, i) + word.length();

    if (i < tlen)
    {
      boolean addSpace = true;
      char nextChar = txt.charAt(i);

      // --- recover non-word symbols from the originally extracted text
      while (!Character.isLetterOrDigit(nextChar) && nextChar != '[')
      {
        txt_segmented.append(nextChar);
        if (++i < tlen) nextChar = txt.charAt(i);
        else            nextChar = 'A';

        addSpace = false;
      }

      if (addSpace) txt_segmented.append(' '); // --- separate with a blank space
    }

    wb = we + 1;
  }

  // --- separate letters from the preceding punctuation marks
  txt_segmented = stretch(txt_segmented);
  // ----------------------------------------------------------


  // ----------------------------------------------------------
  // --- tidy up the extracted text
  // ----------------------------------------------------------
  //   - remove weird lines (possible page numbers, table rows, 
  //     equation numbers, etc.)
  //   - separate titles using HTML tags: <br></br>
  // ----------------------------------------------------------
  String     file_tmp = "tmp_txt.txt";
  FileWriter file_out = fileOut(file_tmp);
             file_out.write(txt_segmented.toString());
             file_out.close();

  txt = new StringBuffer();

  EasyReader file_in = fileIn(file_tmp);
  while (!file_in.eof())
  {
    // --- for each text line
    String line = file_in.readString();

    if (!weird(line))
    {      
      if (title(line)) line = "\n<br></br>\n" + line + "\n<br></br>\n";
      txt.append(line + "\n");
    }
  }
  file_in.close();

  File file = new File(file_tmp); file.delete();
  // ----------------------------------------------------------


  return txt;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- separate letters from the preceding punctutation marks
// ----------------------------------------------------------
public static StringBuffer stretch(StringBuffer txt)
{   
  StringBuffer out = new StringBuffer();
  String punkt = ".,:;!?&";

  int i;
  int l = txt.length();

  for (i = 0; i < l - 1; i++)
  {
    char c = txt.charAt(i);
    out.append(c);

    if (punkt.indexOf(c) >= 0)
    {
      if (Character.isLetter(txt.charAt(i+1))) out.append(' ');
    }
  }

  return out;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- possible title?
// ----------------------------------------------------------
public static boolean title(String line)
{   
  int len = line.length();

  for (int i = 1; i < len; i++)
  {
    int asciiCode = Integer.valueOf(line.charAt(i)).intValue();
    if ('a' < asciiCode && asciiCode < 'z') return false;
  }

  return true;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- possible page number, equation number...?
// ----------------------------------------------------------
public static boolean weird(String line)
{   
  int len = line.length();

  if (len < 3) return true;

  int letters = 0;
  int digits  = 0;
  int spaces  = 0;

  for (int i = 0; i < len; i++)
  {
    char c = line.charAt(i);

         if (Character.isLetter(c))     letters++;
    else if (Character.isDigit(c))      digits++;
    else if (Character.isWhitespace(c)) spaces++;
  }

  int other = len - (letters + digits + spaces);

  double t = len / 10.0;

  if (letters < t*6 || digits > t*3 || other > t*3) return true;
  else                                              return false;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- replace all occurrences of from-string with to-string
//     within the target-text
// ----------------------------------------------------------
public static String replace(String target, String from, String to)
{   
  int start = target.indexOf(from);
  if (start < 0) return target;

  int          lf          = from.length();
  char[]       targetChars = target.toCharArray();
  StringBuffer buffer      = new StringBuffer();
  int          copyFrom    = 0;

  while (start >= 0)
  {
    buffer.append(targetChars, copyFrom, start - copyFrom);
    buffer.append(to);
    copyFrom = start + lf;
    start = target.indexOf(from, copyFrom);
  }

  buffer.append (targetChars, copyFrom, targetChars.length - copyFrom);

  return buffer.toString();
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- open file for WRITING
// ----------------------------------------------------------
private static FileWriter fileOut(String file_name)
{
  FileWriter file = null;
  System.out.print("Writing to: " + file_name + " ... ");
  try {file = new FileWriter(file_name);}
  catch (Exception ex) {System.out.println(ex.toString());}
  System.out.print("File open. ");
  return file;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- open file for READING
// ----------------------------------------------------------
private static EasyReader fileIn(String file_name)
{
  EasyReader file = null;
  System.out.print("Reading from: " + file_name + " ... ");
  try {file = new EasyReader(file_name);}
  catch (Exception ex) {System.out.println(ex.toString());}
  System.out.print("File open. ");
  return file;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- extract TEXT from a PDF file using PDFBOX
// ----------------------------------------------------------
public static StringBuffer extractTextFromPDF(String file_name) throws IOException
{
  StringWriter sw = new StringWriter();
  PDDocument doc  = null;
		 
  try 
  {
    doc = PDDocument.load(file_name);
			 
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setStartPage(1);
    stripper.setEndPage(Integer.MAX_VALUE);
    stripper.writeText(doc, sw);
  }
  finally
  {
    if (doc != null) doc.close();
  }

  StringBuffer sbuf = new StringBuffer();
  sbuf.append(sw.toString());

  int i = sbuf.length() - 1;
  while (i > 0)
  {
    if (sbuf.charAt(i) == '\r' || sbuf.charAt(i) == '\n')
    {
      if (sbuf.charAt(i-1) == '-')
      {
        sbuf.deleteCharAt(i--);   // --- delete '\n' or '\r'
        sbuf.deleteCharAt(i);     // --- delete '-'
      }
    }
    i--;
  }

  return sbuf;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- extract WORDS from a PDF file using JPEDAL
// ----------------------------------------------------------
private static StringBuffer extractWordsFromPDF(String file_name) throws IOException
{
  PdfDecoder decodePdf = null;

  StringBuffer wordsBuffer = new StringBuffer();

  // --- if XML content not require, then *pure text* extraction is much faster
  PdfDecoder.useTextExtraction();
		
  try
  {
    decodePdf = new PdfDecoder(false);
    decodePdf.setExtractionMode(PdfDecoder.TEXT); // --- extract just text
    decodePdf.init(true);

    // --- reset to use unaltered co-ords - allow use of rotated or unrotated
    //     co-ordinates on pages with rotation (used to be in PdfDecoder)
    PdfGroupingAlgorithms.useUnrotatedCoords = false;
			
    decodePdf.openPdfFile(file_name);
  }
  catch (PdfSecurityException e) {System.err.println("Exception " + e + " in pdf code for wordlist" + file_name);}
  catch (PdfException e)         {System.err.println("Exception " + e + " in pdf code for wordlist" + file_name);}
  catch (Exception e)            {System.err.println("Exception " + e + " in pdf code for wordlist" + file_name); e.printStackTrace();}
	
  // --- extract data from pdf (if allowed)
  if(!decodePdf.isExtractionAllowed()) System.out.println("Text extraction from " + file_name + " not allowed!");
  else if (decodePdf.isEncrypted() && !decodePdf.isPasswordSupplied()) System.out.println("Encrypted settings in " + file_name + "!");
  else
  {
    int start = 1, end = decodePdf.getPageCount(); // --- page range: all pages

    // --- extract data from pdf
    try
    {
      for (int page = start; page < end + 1; page++)
      {
        decodePdf.decodePage(page);

        // --- create a grouping object to apply grouping to data
        PdfGroupingAlgorithms currentGrouping = decodePdf.getGroupingObject();

        // --- use whole page size
        PdfPageData currentPageData = decodePdf.getPdfPageData();

        // --- co-ordinates are x1,y1 (top left hand corner), x2,y2 (bottom right)
        int x1 = currentPageData.getMediaBoxX(page);
        int x2 = currentPageData.getMediaBoxWidth(page) + x1;

        int y2 = currentPageData.getMediaBoxX(page);
        int y1 = currentPageData.getMediaBoxHeight(page) - y2;
					
        List words = null;

        try
        {
          words = currentGrouping.extractTextAsWordlist(x1, y1, x2, y2, page, false, true, "&:=()!;.,\\/\"\"\'\'");
        }
        catch (PdfException e)
        {
          decodePdf.closePdfFile();
          System.err.println("Exception = " + e + " in " + file_name);
        }

        if (words == null) System.out.println("No text found in " + file_name + "!");
        else
        {
          Iterator wordIterator = words.iterator();
          while ( wordIterator.hasNext() )
          {
            String currentWord = (String) wordIterator.next();
								
            // --- remove the XML formatting if present - not needed for pure text
            currentWord = Strip.convertToText(currentWord);
								
            int wx1 = (int) Float.parseFloat((String) wordIterator.next());
            int wy1 = (int) Float.parseFloat((String) wordIterator.next());
            int wx2 = (int) Float.parseFloat((String) wordIterator.next());
            int wy2 = (int) Float.parseFloat((String) wordIterator.next());
							
            wordsBuffer.append(currentWord + " ");	
          }
        }

        // --- remove data once written out
        decodePdf.flushObjectValues(false);
      }
    }
    catch (Exception e)
    {
      decodePdf.closePdfFile();
      System.err.println("Exception " + e + " in " + file_name);
      e.printStackTrace();
    }

    // --- flush data structures - not strictly required
    decodePdf.flushObjectValues(true);

    System.out.println("\nText read from " + file_name + ".");
  }

  decodePdf.closePdfFile();
  decodePdf = null;

  return wordsBuffer;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- put hyphen-broken words back together
// ----------------------------------------------------------
private static String fixHyphenation(StringBuffer wordsBuffer)
{
  String words = wordsBuffer.toString() + " ";

  // --- fix some of the hyphenation problems
  words = words.replaceAll(" \\- ", " ");
  words = words.replaceAll("\\- ", "");
  words = words.replaceAll(" \\-", "-");
  words = words.replaceAll("  ", " ");

  return words;
}
// ----------------------------------------------------------


// ----------------------------------------------------------
// --- find the last mention of the literature cited
// ----------------------------------------------------------
private static int indexOfReferences(String txt) throws IOException
{
  String lowerCased = txt.toLowerCase();

  int index[] = new int [3];
  index[0] = lowerCased.lastIndexOf("references");
  index[1] = lowerCased.lastIndexOf("literature cited");
  index[2] = lowerCased.lastIndexOf("citations");

  int max = -1;

  for (int i = 0; i < 3; i++)
  {
    if (index[i] > max) max = index[i];
  }

  return max;
}
// ----------------------------------------------------------

}