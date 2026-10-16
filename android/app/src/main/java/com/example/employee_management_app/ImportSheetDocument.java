package com.example.employee_management_app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Read/write the first XLSX worksheet or CSV without an external Excel app.
 * Keeps the other XLSX zip entries unchanged. Edits existing cells only.
 * Does not evaluate formulas or retain formula results in edited cells.
 */
public final class ImportSheetDocument {
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private final boolean xlsx;
    private final LinkedHashMap<String,byte[]> zipFiles = new LinkedHashMap<>();
    private Document sheet;
    private final List<List<String>> rows = new ArrayList<>();
    private final Map<String,String> edits = new LinkedHashMap<>();
    private final List<List<String>> originallyLoaded = new ArrayList<>();
    private boolean bom;
    private char csvDelimiter=',';

    private ImportSheetDocument(boolean xlsx) { this.xlsx = xlsx; }
    public static ImportSheetDocument open(InputStream in, String name) throws Exception {
        ImportSheetDocument doc = new ImportSheetDocument(name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"));
        byte[] bytes = readLimited(in, 3*1024*1024);
        if (doc.xlsx) doc.loadXlsx(bytes); else doc.loadCsv(bytes);
        if (doc.rows.size() < 2 || doc.rows.size() > 501) throw new IllegalArgumentException("Cần tiêu đề và 1–500 nhân viên");
        if (doc.rows.get(0).size() > 20) throw new IllegalArgumentException("File có quá nhiều cột");
        for (List<String> row : doc.rows) doc.originallyLoaded.add(new ArrayList<>(row));
        return doc;
    }
    public List<String> headers() { return Collections.unmodifiableList(rows.get(0)); }
    public int dataCount() { return rows.size()-1; }
    public List<String> row(int i) { return Collections.unmodifiableList(rows.get(i+1)); }
    public boolean hasEdits() { return !edits.isEmpty(); }
    public void setValue(int dataRow, int column, String value) {
        if (dataRow < 0 || dataRow >= dataCount() || column < 0 || column >= headers().size()) throw new IllegalArgumentException("Ô không hợp lệ");
        String next = value == null ? "" : value;
        if (next.length() > 500) throw new IllegalArgumentException("Giá trị ô quá dài");
        List<String> r = rows.get(dataRow+1);
        while (r.size() < headers().size()) r.add("");
        String key = (dataRow+2)+":"+column;
        r.set(column,next);
        if (originallyLoaded.get(dataRow+1).get(column).equals(next)) edits.remove(key);
        else edits.put(key,next);
    }
    public byte[] save() throws Exception {
        if (!xlsx) return writeCsv();
        Element data = first(sheet.getDocumentElement(), "sheetData");
        if (data == null) throw new IllegalArgumentException("Không có sheetData");
        for (Map.Entry<String,String> e : edits.entrySet()) {
            String[] key = e.getKey().split(":");
            int rowNumber = Integer.parseInt(key[0]);
            int colIndex = Integer.parseInt(key[1]);
            Element row = null;
            for (Element r : children(data,"row")) if (Integer.toString(rowNumber).equals(r.getAttribute("r"))) { row=r; break; }
            if (row == null) throw new IllegalArgumentException("Không tìm thấy dòng " + rowNumber);
            String ref = columnRef(colIndex) + rowNumber;
            Element cell = null;
            for (Element c : children(row,"c")) if (ref.equals(c.getAttribute("r"))) {cell=c; break;}
            if (cell == null) {
                cell = sheet.createElementNS(NS,"c"); cell.setAttribute("r",ref);
                Node before=null;
                for (Element c : children(row,"c")) if (columnIndex(c.getAttribute("r")) > colIndex) { before=c; break; }
                if (before == null) row.appendChild(cell); else row.insertBefore(cell,before);
            }
            while (cell.hasChildNodes()) cell.removeChild(cell.getFirstChild());
            cell.setAttribute("t","inlineStr");
            Element is=sheet.createElementNS(NS,"is"), t=sheet.createElementNS(NS,"t");
            t.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");
            t.setTextContent(e.getValue()); is.appendChild(t); cell.appendChild(is);
        }
        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        TransformerFactory tf = TransformerFactory.newInstance();
        try { tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true); }
        catch (javax.xml.transform.TransformerConfigurationException ignored) { /* safe: DOM input only */ }
        tf.newTransformer().transform(new DOMSource(sheet),new StreamResult(xml));
        zipFiles.put("xl/worksheets/sheet1.xml",xml.toByteArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(output)) {
            for (Map.Entry<String,byte[]> entry : zipFiles.entrySet()) {
                zout.putNextEntry(new ZipEntry(entry.getKey()));
                zout.write(entry.getValue()); zout.closeEntry();
            }
        }
        byte[] result = output.toByteArray();
        if (result.length > 3*1024*1024) throw new IllegalArgumentException("File kết quả vượt quá 3 MB");
        return result;
    }
    private void loadXlsx(byte[] bytes) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int total=0;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] data=readLimited(in, 12*1024*1024);
                total += data.length;
                if (total > 32*1024*1024 || zipFiles.size() > 500) throw new IllegalArgumentException("Excel quá lớn");
                zipFiles.put(entry.getName(),data);
            }
        }
        byte[] raw=zipFiles.get("xl/worksheets/sheet1.xml");
        if (raw == null) throw new IllegalArgumentException("Không tìm thấy Sheet1");
        sheet=parseXml(raw);
        List<String> shared=new ArrayList<>();
        if (zipFiles.containsKey("xl/sharedStrings.xml")) {
            Document strs=parseXml(zipFiles.get("xl/sharedStrings.xml"));
            NodeList nodes=strs.getElementsByTagNameNS(NS,"si");
            for (int i=0;i<nodes.getLength();i++) {
                StringBuilder text=new StringBuilder();
                NodeList ts=((Element)nodes.item(i)).getElementsByTagNameNS(NS,"t");
                for (int j=0;j<ts.getLength();j++) text.append(ts.item(j).getTextContent());
                shared.add(text.toString());
            }
        }
        Element data=first(sheet.getDocumentElement(),"sheetData");
        if (data == null) throw new IllegalArgumentException("Excel không có sheetData");
        for (Element r : children(data,"row")) {
            // Editors update cell coordinates using their worksheet row number. Reject
            // sparse/nonconsecutive rows so a visual row can never write another row.
            String expected = Integer.toString(rows.size() + 1);
            if (!expected.equals(r.getAttribute("r"))) {
                throw new IllegalArgumentException("Các dòng Excel phải liên tục từ dòng 1 (thiếu hoặc lệch dòng " + expected + ")");
            }
            List<String> values=new ArrayList<>();
            for (Element c : children(r,"c")) {
                int col=columnIndex(c.getAttribute("r"));
                if (col<0 || col>20) throw new IllegalArgumentException("File quá nhiều cột");
                while(values.size()<=col) values.add("");
                String type=c.getAttribute("t");
                String rawVal="";
                if ("inlineStr".equals(type)) {
                    StringBuilder text=new StringBuilder();
                    NodeList ts=c.getElementsByTagNameNS(NS,"t");
                    for(int j=0;j<ts.getLength();j++) text.append(ts.item(j).getTextContent());
                    rawVal=text.toString();
                } else {
                    Element value=first(c,"v");
                    if(value!=null) rawVal=value.getTextContent();
                    if("s".equals(type)) {
                        try { rawVal=shared.get(Integer.parseInt(rawVal)); }
                        catch(Exception ex) { rawVal=""; }
                    }
                }
                values.set(col,rawVal);
            }
            if (!values.isEmpty()) rows.add(values);
        }
        normalizeRows();
    }
    private void loadCsv(byte[] bytes) {
        String text=new String(bytes,StandardCharsets.UTF_8);
        bom=text.startsWith("\uFEFF");
        if(bom) text=text.substring(1);
        String firstLine=text.split("\r?\n",2)[0];
        if(firstLine.chars().filter(c->c==';').count()>firstLine.chars().filter(c->c==',').count()) csvDelimiter=';';
        // Quote-aware parser supports comma/semicolon, CRLF and escaped quotes.
        StringBuilder cell=new StringBuilder(); List<String> row=new ArrayList<>(); boolean quoted=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='"') {
                if(quoted && i+1<text.length() && text.charAt(i+1)=='"') { cell.append('"');i++; }
                else quoted=!quoted;
            } else if(c==csvDelimiter && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if((c=='\r'||c=='\n') && !quoted) {
                if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if(row.size()>1 || !row.get(0).isEmpty()) rows.add(row);
                row=new ArrayList<>();
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("CSV có dấu nháy kép chưa đóng");
        if(cell.length()>0 || !row.isEmpty()) { row.add(cell.toString());rows.add(row); }
        normalizeRows();
    }
    private byte[] writeCsv() {
        StringBuilder result=new StringBuilder(); if(bom) result.append('\uFEFF');
        for(List<String> row:rows) {
            for(int i=0;i<row.size();i++) {
                if(i>0) result.append(csvDelimiter);
                String val=row.get(i);
                if(val.indexOf(csvDelimiter)>=0||val.indexOf('"')>=0||val.indexOf('\n')>=0||val.indexOf('\r')>=0) result.append('"').append(val.replace("\"","\"\"")).append('"');
                else result.append(val);
            }
            result.append("\r\n");
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void normalizeRows() {
        if(rows.isEmpty()) return;
        int cols=rows.get(0).size();
        for(List<String> row:rows) while(row.size()<cols) row.add("");
    }
    private static Document parseXml(byte[] bytes) throws Exception {
        // XLSX worksheet XML never needs DTD. Block it even if Android's XML provider
        // does not implement all optional parser features.
        String contents=new String(bytes,StandardCharsets.UTF_8);
        if(contents.toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE")
                || contents.toUpperCase(java.util.Locale.ROOT).contains("<!ENTITY"))
            throw new IllegalArgumentException("XML có DTD/ENTITY không an toàn");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try {f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}
        catch(javax.xml.parsers.ParserConfigurationException ignored) { }
        try {f.setFeature("http://xml.org/sax/features/external-general-entities",false);}
        catch(javax.xml.parsers.ParserConfigurationException ignored) { }
        try {f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}
        catch(javax.xml.parsers.ParserConfigurationException ignored) { }
        try {f.setXIncludeAware(false);}catch(UnsupportedOperationException ignored){ }
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static Element first(Element parent,String name) {
        NodeList nodes=parent.getElementsByTagNameNS(NS,name);
        return nodes.getLength()==0?null:(Element)nodes.item(0);
    }
    private static List<Element> children(Element parent,String name) {
        List<Element> result=new ArrayList<>();
        NodeList nodes=parent.getChildNodes();
        for(int i=0;i<nodes.getLength();i++) {
            Node child=nodes.item(i);
            if(child instanceof Element && name.equals(child.getLocalName()) && NS.equals(child.getNamespaceURI())) result.add((Element)child);
        }
        return result;
    }
    private static int columnIndex(String ref) {
        int result=0; int i=0;
        while(i<ref.length() && Character.isLetter(ref.charAt(i))) {
            result=result*26+Character.toUpperCase(ref.charAt(i))- 'A'+1; i++;
        }
        return result-1;
    }
    private static String columnRef(int index) {
        StringBuilder sb=new StringBuilder();
        for(int n=index+1;n>0;n=(n-1)/26) sb.insert(0,(char)('A'+(n-1)%26));
        return sb.toString();
    }
    private static byte[] readLimited(InputStream in,int max) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192]; int n;
        while((n=in.read(buffer))!=-1) {
            if(out.size()+n>max) throw new IllegalArgumentException("File quá lớn");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
}
