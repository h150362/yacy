// wikiBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 20.07.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMap;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.server.serverCodings;

public class wikiBoard {
    
    public  static final int keyLength = 64;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    private kelondroMap datbase = null;
    private kelondroMap bkpbase = null;
    private HashMap authors = new HashMap();
    
    public wikiBoard(File actpath, File bkppath, int bufferkb) {
    		new File(actpath.getParent()).mkdir();
        if (datbase == null) {
            if (actpath.exists()) try {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x40));
            } catch (IOException e) {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x400, keyLength, recordSize, true));
            } else {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x400, keyLength, recordSize, true));
            }
        }
        new File(bkppath.getParent()).mkdir();
        if (bkpbase == null) {
            if (bkppath.exists()) try {
                bkpbase = new kelondroMap(new kelondroDyn(bkppath, bufferkb / 2 * 0x400));
            } catch (IOException e) {
                bkpbase = new kelondroMap(new kelondroDyn(bkppath, bufferkb / 2 * 0x400, keyLength + dateFormat.length(), recordSize, true));
            } else {
                bkpbase = new kelondroMap(new kelondroDyn(bkppath, bufferkb / 2 * 0x400, keyLength + dateFormat.length(), recordSize, true));
            }
        }
    }

    public int sizeOfTwo() {
        return datbase.size() + bkpbase.size();
    }
    
    public int size() {
        return datbase.size();
    }
    
    public int[] dbCacheChunkSize() {
        int[] db = datbase.cacheChunkSize();
        int[] bk = bkpbase.cacheChunkSize();
        int[] i = new int[3];
        i[kelondroRecords.CP_LOW] = (db[kelondroRecords.CP_LOW] + bk[kelondroRecords.CP_LOW]) / 2;
        i[kelondroRecords.CP_MEDIUM] = (db[kelondroRecords.CP_MEDIUM] + bk[kelondroRecords.CP_MEDIUM]) / 2;
        i[kelondroRecords.CP_HIGH] = (db[kelondroRecords.CP_HIGH] + bk[kelondroRecords.CP_HIGH]) / 2;
        return i;
    }
    
    public int[] dbCacheFillStatus() {
        int[] a = datbase.cacheFillStatus();
        int[] b = bkpbase.cacheFillStatus();
        return new int[]{a[0] + b[0], a[1] + b[1], a[2] + b[2], a[3] + b[3]};
    }
    
    public void close() {
        try {datbase.close();} catch (IOException e) {}
        try {bkpbase.close();} catch (IOException e) {}
    }
    
    private static String dateString() {
	return dateString(new GregorianCalendar(GMTTimeZone).getTime());
    }

    private static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }

    private static String normalize(String key) {
        if (key == null) return "null";
        return key.trim().toLowerCase();
    }

    public static String webalize(String key) {
        if (key == null) return "null";
        key = key.trim().toLowerCase();
        int p;
        while ((p = key.indexOf(" ")) >= 0)
            key = key.substring(0, p) + "%20" + key.substring(p +1);
        return key;
    }
    
    public String guessAuthor(String ip) {
        String author = (String) authors.get(ip);
        //yacyCore.log.logDebug("DEBUG: guessing author for ip = " + ip + " is '" + author + "', authors = " + authors.toString());
        return author;
    }

    public entry newEntry(String subject, String author, String ip, String reason, byte[] page) {
	return new entry(normalize(subject), author, ip, reason, page);
    }

    public class entry {
	
	String key;
        Map record;

	public entry(String subject, String author, String ip, String reason, byte[] page) {
	    record = new HashMap();
	    key = subject;
	    if (key.length() > keyLength) key = key.substring(0, keyLength);
	    record.put("date", dateString());
	    if ((author == null) || (author.length() == 0)) author = "anonymous";
	    record.put("author", serverCodings.enhancedCoder.encodeBase64(author.getBytes()));
	    if ((ip == null) || (ip.length() == 0)) ip = "";
	    record.put("ip", ip);
	    if ((reason == null) || (reason.length() == 0)) reason = "";
	    record.put("reason", serverCodings.enhancedCoder.encodeBase64(reason.getBytes()));
	    if (page == null)
		record.put("page", "");
	    else
		record.put("page", serverCodings.enhancedCoder.encodeBase64(page));
            authors.put(ip, author);
            //System.out.println("DEBUG: setting author " + author + " for ip = " + ip + ", authors = " + authors.toString());
	}

	private entry(String key, Map record) {
	    this.key = key;
	    this.record = record;
	}

	public String subject() {
	    return key;
	}

	public Date date() {
	    try {
		String c = (String) record.get("date");
		return SimpleFormatter.parse(c);
	    } catch (ParseException e) {
		return new Date();
	    }
	}

	public String author() {
	    String a = (String) record.get("author");
	    if (a == null) return "anonymous";
	    byte[] b = serverCodings.enhancedCoder.decodeBase64(a);
	    if (b == null) return "anonymous";
	    return new String(b);
	}

	public String reason() {
	    String r = (String) record.get("reason");
	    if (r == null) return "";
	    byte[] b = serverCodings.enhancedCoder.decodeBase64(r);
	    if (b == null) return "unknown";
	    return new String(b);
	}

	public byte[] page() {
	    String m = (String) record.get("page");
	    if (m == null) return new byte[0];
	    byte[] b = serverCodings.enhancedCoder.decodeBase64(m);
	    if (b == null) return "".getBytes();
	    return b;
	}
        
	private void setAncestorDate(Date date) {
	    record.put("bkp", dateString(date));
	}

	private Date getAncestorDate() {
	    try {
		String c = (String) record.get("date");
		if (c == null) return null;
		return SimpleFormatter.parse(c);
	    } catch (ParseException e) {
		return null;
	    }
	}

        /*
	public boolean hasAncestor() {
	    Date ancDate = getAncestorDate();
	    if (ancDate == null) return false;
	    try {
		return bkpbase.has(key + dateString(ancDate));
	    } catch (IOException e) {
		return false;
	    }
	}
        */
        
	public entry getAncestor() {
	    Date ancDate = getAncestorDate();
	    if (ancDate == null) return null;
	    return read(key + dateString(ancDate), bkpbase);
	}

 	private void setChild(String subject) {
	    record.put("child", serverCodings.enhancedCoder.encodeBase64(subject.getBytes()));
	}
        
        private String getChildName() {
            String c = (String) record.get("child");
	    if (c == null) return null;
	    byte[] subject = serverCodings.enhancedCoder.decodeBase64(c);
            if (subject == null) return null;
            return new String(subject);
	}
        
        public boolean hasChild() {
            String c = (String) record.get("child");
	    if (c == null) return false;
	    byte[] subject = serverCodings.enhancedCoder.decodeBase64(c);
            return (subject != null);
	}

        public entry getChild() {
	    String childName = getChildName();
	    if (childName == null) return null;
	    return read(childName, datbase);
	}
    }

    public String write(entry page) {
	// writes a new page and returns key
	try {
	    // first load the old page
	    entry oldEntry = read(page.key);
	    // set the bkp date of the new page to the date of the old page
	    Date oldDate = oldEntry.date();
	    page.setAncestorDate(oldDate);
            oldEntry.setChild(page.subject());
	    // write the backup
            //System.out.println("key = " + page.key);
            //System.out.println("oldDate = " + oldDate);
            //System.out.println("record = " + oldEntry.record.toString());
	    bkpbase.set(page.key + dateString(oldDate), oldEntry.record);
	    // write the new page
	    datbase.set(page.key, page.record);
	    return page.key;
	} catch (IOException e) {
	    return null;
	}
    }

    public entry read(String key) {
	return read(key, datbase);
    }

    private entry read(String key, kelondroMap base) {
	try {
            key = normalize(key);
            if (key.length() > keyLength) key = key.substring(0, keyLength);
	    Map record = base.get(key);
	    if (record == null) return newEntry(key, "anonymous", "127.0.0.1", "New Page", "".getBytes());
        return new entry(key, record);
	} catch (IOException e) {
	    return null;
	}
    }

    /*
    public boolean has(String key) {
	try {
	    return datbase.has(normalize(key));
	} catch (IOException e) {
	    return false;
	}
    }
    */
    
    public Iterator keys(boolean up) throws IOException {
	return datbase.keys(up, false);
    }

}
