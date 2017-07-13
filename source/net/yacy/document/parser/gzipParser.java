//gzipParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.kelondro.util.FileUtils;
import org.apache.commons.compress.compressors.gzip.GzipUtils;

/**
 * Parses a gz archive.
 * Unzips and parses the content and adds it to the created main document
 */
public class gzipParser extends AbstractParser implements Parser {

    public gzipParser() {
        super("GNU Zip Compressed Archive Parser");
        this.SUPPORTED_EXTENSIONS.add("gz");
        this.SUPPORTED_EXTENSIONS.add("tgz");
        this.SUPPORTED_MIME_TYPES.add("application/x-gzip");
        this.SUPPORTED_MIME_TYPES.add("application/gzip");
        this.SUPPORTED_MIME_TYPES.add("application/x-gunzip");
        this.SUPPORTED_MIME_TYPES.add("application/gzipped");
        this.SUPPORTED_MIME_TYPES.add("application/gzip-compressed");
        this.SUPPORTED_MIME_TYPES.add("gzip/document");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        File tempFile = null;
        Document maindoc = null;
        GZIPInputStream zippedContent = null;
        FileOutputStream out = null;
        try {
            int read = 0;
            final byte[] data = new byte[1024];

            zippedContent = new GZIPInputStream(source);

            tempFile = File.createTempFile("gunzip","tmp");

            // creating a temp file to store the uncompressed data
            out = new FileOutputStream(tempFile);

            // reading gzip file and store it uncompressed
            while ((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
        } catch(Exception e) {
        	if (tempFile != null) {
        		FileUtils.deletedelete(tempFile);
        	}
        	throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
        } finally {
        	if(zippedContent != null) {
        		try {
					zippedContent.close();
				} catch (IOException ignored) {
					log.warn("Could not close gzip input stream");
				}
        	}
        	if(out != null) {
        		try {
					out.close();
				} catch (IOException e) {
					throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
				}
        	}
        }
        try {
            maindoc = createMainDocument(location, mimeType, charset);
            // creating a new parser class to parse the unzipped content
            final String contentfilename = GzipUtils.getUncompressedFilename(location.getFileName());
            final String mime = TextParser.mimeOf(MultiProtocolURL.getFileExtension(contentfilename));
            Document[] docs = TextParser.parseSource(location, mime, null, scraper, timezoneOffset, 999, tempFile);
            if (docs != null) maindoc.addSubDocuments(docs);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
        return maindoc == null ? null : new Document[]{maindoc};
    }

    /**
     * Create the main parsed document for this gzip container, register with supplied url & mime type
     * @param location the parsed resource URL
     * @param mimeType the media type of the resource
     * @param charset the charset name if known
     * @return a Document instance
     */
	private Document createMainDocument(final DigestURL location, final String mimeType, final String charset) {
		final String filename = location.getFileName();
		Document maindoc = new Document(
		        location,
		        mimeType,
		        charset,
		        this,
		        null,
		        null,
		        AbstractParser.singleList(filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename)), // title
		        null,
		        null,
		        null,
		        null,
		        0.0d, 0.0d,
		        (Object) null,
		        null,
		        null,
		        null,
		        false,
		        new Date());
		return maindoc;
	}
    
    @Override
    public boolean isParseWithLimitsSupported() {
    	return true;
    }
    
    @Override
    public Document[] parseWithLimits(final DigestURL location, final String mimeType, final String charset, final VocabularyScraper scraper,
    		final int timezoneOffset, final InputStream source, final int maxLinks, final long maxBytes)
    		throws Parser.Failure {
        Document maindoc = null;
        GZIPInputStream zippedContent = null;
        try {
        	/* Only use in-memory stream here (no temporary file) : the parsers 
        	 * matching compressed content are expected to handle properly the maxBytes limit and terminate 
        	 * before an eventual OutOfMemory occurs */
            zippedContent = new GZIPInputStream(source);
        } catch(IOException e) {
        	throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
        }
        try {
            maindoc = createMainDocument(location, mimeType, charset);
            // creating a new parser class to parse the unzipped content
            final String contentfilename = GzipUtils.getUncompressedFilename(location.getFileName());
            final String mime = TextParser.mimeOf(MultiProtocolURL.getFileExtension(contentfilename));
            
            /* Rely on the supporting parsers to respect the maxLinks and maxBytes limits on compressed content */
            Document[] docs = TextParser.parseWithLimits(location, mime, charset, timezoneOffset, -1, zippedContent, maxLinks, maxBytes);
            if (docs != null) maindoc.addSubDocuments(docs);
        } catch (final Exception e) {
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(),location);
        }
        return maindoc == null ? null : new Document[]{maindoc};
    }

}
