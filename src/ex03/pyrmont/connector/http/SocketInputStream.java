package ex03.pyrmont.connector.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.EOFException;
import org.apache.catalina.util.StringManager;

/**
 * SocketInputStream的读取逻辑：（也是大部分字节流的读取逻辑）
 * step1、初始化buf的数组长度，
 * step2、每次从inputStream中读取一段buf.length的数据，
 * step3、遍历buf数组，把buf的数据解析
 * step4、如果buf读取完毕，则从InputStream中继续读取一段buf.length的数据，
 * step5、回到step3,一直轮询，知道inputStream的数据读取完毕
 * Extends InputStream to be more efficient reading lines during HTTP
 * header processing.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @deprecated
 */
public class SocketInputStream extends InputStream {


    // -------------------------------------------------------------- Constants


    /**
     * CR.
     */
    private static final byte CR = (byte) '\r';//回车


    /**
     * LF.
     */
    private static final byte LF = (byte) '\n';//换行


    /**
     * SP.
     */
    private static final byte SP = (byte) ' ';


    /**
     * HT.
     */
    private static final byte HT = (byte) '\t';


    /**
     * COLON.
     */
    private static final byte COLON = (byte) ':';


    /**
     * Lower case offset.
     */
    private static final int LC_OFFSET = 'A' - 'a';


    /**
     * Internal buffer.
     */
    protected byte buf[];//1byte为一个字节


    /**
     * Last valid byte.
     */
    protected int count;//记录缓冲区内有效的字节数,当pos游标大于等于count时，结束buf的读取


    /**
     * Position in the buffer.
     */
    protected int pos;//缓冲区buf中的游标


    /**
     * Underlying input stream.
     */
    protected InputStream is;//输入流


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a servlet input stream associated with the specified socket
     * input.
     *
     * @param is socket input stream
     * @param bufferSize size of the internal buffer
     */
    public SocketInputStream(InputStream is, int bufferSize) {

        this.is = is;
        buf = new byte[bufferSize];

    }


    // -------------------------------------------------------------- Variables


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables


    // --------------------------------------------------------- Public Methods


    /**
     * 读取请求头：请求方式 uri 协议
     * Read the request line, and copies it to the given buffer. This
     * function is meant to be used during the HTTP request header parsing.
     * Do NOT attempt to read the request body using it.
     *
     * @param requestLine Request line object
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accomodate
     * the whole line.
     */
    public void readRequestLine(HttpRequestLine requestLine)
        throws IOException {

        // Recycling check
        if (requestLine.methodEnd != 0)
            requestLine.recycle();

        //跳过文件头的CR和LF
        // Checking for a blank line
        int chr = 0;
        do { // Skipping CR or LF
            try {
                chr = read();//向buf中填充从InputStream读取到的内容
            } catch (IOException e) {
                chr = -1;
            }
        } while ((chr == CR) || (chr == LF));
        if (chr == -1)
            throw new EOFException
                (sm.getString("requestStream.readline.error"));
        pos--;

        // Reading the method name  读取http请求方式

        int maxRead = requestLine.method.length;//最大长度默认为8位(POST)
        int readStart = pos;
        int readCount = 0;

        boolean space = false;//用来标记当前游标的位置是否为空格

        //轮询buf，直到遇到空格
        while (!space) {
            // if the buffer is full, extend it ，在遇到空格前，一直做byte的拼接,保证本次while循环能够读取到完整的请求方法信息（GET,POST）
            if (readCount >= maxRead) {
                if ((2 * maxRead) <= HttpRequestLine.MAX_METHOD_SIZE) {
                    char[] newBuffer = new char[2 * maxRead];//每次读取2*8的长度
                    System.arraycopy(requestLine.method, 0, newBuffer, 0,
                                     maxRead);
                    requestLine.method = newBuffer;
                    maxRead = requestLine.method.length;
                } else {
                    throw new IOException
                        (sm.getString("requestStream.readline.toolong"));
                }
            }
            // We're at the end of the internal buffer buf已经读取完了，则buf需要重新从is(InputStream中读取数据)
            if (pos >= count) {
                int val = read();
                if (val == -1) {
                    throw new IOException
                        (sm.getString("requestStream.readline.error"));
                }
                pos = 0;
                readStart = 0;
            }
            if (buf[pos] == SP) {
                space = true;//读取到空格，读取http请求方式的操作完成
            }
            requestLine.method[readCount] = (char) buf[pos];
            readCount++;
            pos++;
        }

        requestLine.methodEnd = readCount - 1;

        // Reading URI

        maxRead = requestLine.uri.length;
        readStart = pos;
        readCount = 0;

        space = false;

        boolean eol = false;

        while (!space) {
            // if the buffer is full, extend it，在遇到空格前，一直做byte的拼接,保证本次while循环能够读取到完整的uri信息
            if (readCount >= maxRead) {
                if ((2 * maxRead) <= HttpRequestLine.MAX_URI_SIZE) {
                    char[] newBuffer = new char[2 * maxRead];
                    System.arraycopy(requestLine.uri, 0, newBuffer, 0,
                                     maxRead);
                    requestLine.uri = newBuffer;
                    maxRead = requestLine.uri.length;
                } else {
                    throw new IOException
                        (sm.getString("requestStream.readline.toolong"));
                }
            }
            // We're at the end of the internal buffer
            if (pos >= count) {
                int val = read();
                if (val == -1)
                    throw new IOException
                        (sm.getString("requestStream.readline.error"));
                pos = 0;
                readStart = 0;
            }
            if (buf[pos] == SP) {
                space = true;
            } else if ((buf[pos] == CR) || (buf[pos] == LF)) {
                // HTTP/0.9 style request
                eol = true;
                space = true;
            }
            requestLine.uri[readCount] = (char) buf[pos];
            readCount++;
            pos++;
        }

        requestLine.uriEnd = readCount - 1;

        // Reading protocol

        maxRead = requestLine.protocol.length;
        readStart = pos;
        readCount = 0;

        while (!eol) {
            // if the buffer is full, extend it 为了确保读取到header的name和key，如果buf满了，则扩容
            if (readCount >= maxRead) {
                if ((2 * maxRead) <= HttpRequestLine.MAX_PROTOCOL_SIZE) {
                    char[] newBuffer = new char[2 * maxRead];
                    System.arraycopy(requestLine.protocol, 0, newBuffer, 0,
                                     maxRead);
                    requestLine.protocol = newBuffer;
                    maxRead = requestLine.protocol.length;
                } else {
                    throw new IOException
                        (sm.getString("requestStream.readline.toolong"));
                }
            }
            // We're at the end of the internal buffer
            if (pos >= count) {
                // Copying part (or all) of the internal buffer to the line
                // buffer
                //buf已经读取完毕了
                int val = read();//再次从InputStream读取信息
                if (val == -1)
                    throw new IOException
                        (sm.getString("requestStream.readline.error"));
                pos = 0;
                readStart = 0;
            }
            if (buf[pos] == CR) {
                // Skip CR.
            } else if (buf[pos] == LF) {
                eol = true;//换行符作为当前header已经读取完的标记
            } else {
                requestLine.protocol[readCount] = (char) buf[pos];
                readCount++;
            }
            pos++;
        }

        requestLine.protocolEnd = readCount;

    }


    public void readHeader(HttpHeader header)
        throws IOException {

        // Recycling check
        if (header.nameEnd != 0)
            header.recycle();

        // Checking for a blank line 跳过CR(回车) LF（换行）
        int chr = read();
        if ((chr == CR) || (chr == LF)) { // Skipping CR
            if (chr == CR)
                read(); // Skipping LF
            header.nameEnd = 0;
            header.valueEnd = 0;
            return;
        } else {
            pos--;
        }

        // Reading the header name

        int maxRead = header.name.length;
        int readStart = pos;
        int readCount = 0;

        boolean colon = false;

        while (!colon) {//读取header的name
            // if the buffer is full, extend it  如果缓冲区buf满了，则扩展缓冲区，并拷贝数据
            if (readCount >= maxRead) {
                if ((2 * maxRead) <= HttpHeader.MAX_NAME_SIZE) {
                    char[] newBuffer = new char[2 * maxRead];
                    System.arraycopy(header.name, 0, newBuffer, 0, maxRead);
                    header.name = newBuffer;
                    maxRead = header.name.length;
                } else {
                    throw new IOException
                        (sm.getString("requestStream.readline.toolong"));
                }
            }
            // We're at the end of the internal buffer 缓冲区读取完成，继续从InputStream中读取数据到缓冲区
            if (pos >= count) {
                int val = read();
                if (val == -1) {
                    throw new IOException
                        (sm.getString("requestStream.readline.error"));
                }
                pos = 0;
                readStart = 0;
            }
            if (buf[pos] == COLON) {//如果读取到冒号':' ,表示header的key读取完毕
                colon = true;//TODO 缓冲区还剩下的部分(pos~count这部分的数据)未读出的内容怎么处理？
            }
            char val = (char) buf[pos];
            if ((val >= 'A') && (val <= 'Z')) {//大写转换成小写
                val = (char) (val - LC_OFFSET);
            }
            header.name[readCount] = val;
            readCount++;
            pos++;
        }

        header.nameEnd = readCount - 1;

        // Reading the header value (which can be spanned over multiple lines) 读取请求头的value，可能会跨越多行

        maxRead = header.value.length;
        readStart = pos;
        readCount = 0;

        int crPos = -2;

        boolean eol = false;
        boolean validLine = true;

        while (validLine) {

            boolean space = true;

            // Skipping spaces
            // Note : Only leading white spaces are removed. Trailing white
            // spaces are not.
            while (space) {
                // We're at the end of the internal buffer
                if (pos >= count) {//已经读取完成
                    // Copying part (or all) of the internal buffer to the line
                    // buffer
                    int val = read();//TODO 缓冲区还剩下的部分未读出的内容怎么处理？ 如果pos<count,则继续读完buf的数据
                    if (val == -1)
                        throw new IOException
                            (sm.getString("requestStream.readline.error"));
                    pos = 0;//已经读取完成，则pos重置为0，下一次读取从buf的开头读取
                    readStart = 0;
                }
                if ((buf[pos] == SP) || (buf[pos] == HT)) {//跳过空格" "和制表符"\t"
                    pos++;
                } else {
                    space = false;
                }
            }
            //读取一行  eol表示是否换行
            while (!eol) {
                // if the buffer is full, extend it
                if (readCount >= maxRead) {
                    if ((2 * maxRead) <= HttpHeader.MAX_VALUE_SIZE) {
                        char[] newBuffer = new char[2 * maxRead];
                        System.arraycopy(header.value, 0, newBuffer, 0,
                                         maxRead);
                        header.value = newBuffer;
                        maxRead = header.value.length;
                    } else {
                        throw new IOException
                            (sm.getString("requestStream.readline.toolong"));
                    }
                }
                // We're at the end of the internal buffer 已经读取完成
                if (pos >= count) {
                    // Copying part (or all) of the internal buffer to the line
                    // buffer
                    int val = read();
                    if (val == -1)
                        throw new IOException
                            (sm.getString("requestStream.readline.error"));
                    pos = 0;
                    readStart = 0;
                }
                if (buf[pos] == CR) {
                } else if (buf[pos] == LF) {// \n
                    eol = true;//换行则跳出循环
                } else {//继续读取数据
                    // FIXME : Check if binary conversion is working fine
                    int ch = buf[pos] & 0xff;
                    header.value[readCount] = (char) ch;
                    readCount++;
                }
                pos++;
            }

            int nextChr = read();//继续从InputStream中读取数据

            if ((nextChr != SP) && (nextChr != HT)) {//如果再次遇到非空格和非制表符\t，则表示header的value读取完毕，跳出大循环
                pos--;
                validLine = false;
            } else {
                eol = false;
                // if the buffer is full, extend it
                if (readCount >= maxRead) {
                    if ((2 * maxRead) <= HttpHeader.MAX_VALUE_SIZE) {
                        char[] newBuffer = new char[2 * maxRead];
                        System.arraycopy(header.value, 0, newBuffer, 0,
                                         maxRead);
                        header.value = newBuffer;
                        maxRead = header.value.length;
                    } else {
                        throw new IOException
                            (sm.getString("requestStream.readline.toolong"));
                    }
                }
                header.value[readCount] = ' ';
                readCount++;
            }

        }

        header.valueEnd = readCount;

    }


    /**
     * Read byte.
     * 如果buff内还有数据未读取，则不会重新刷新buf的内容，返回当前游标对应缓冲区的值
     * 按字节读取数据到buf
     */
    public int read()
        throws IOException {
        if (pos >= count) {//count表示读取到buf的最后的有效的位置，如果pos的位置大于count，则需要重新去is（InputStream）中获取数据
            fill();
            if (pos >= count)
                return -1;
        }
        //如果缓冲区内还有数据，则不再继续读取InputStream的数据
        return buf[pos++] & 0xff;//oxff: 1111 1111  取低buf[pos++]的低八位  返回下一个需要读取的数据
    }


    /**
     *
     */
    /*
    public int read(byte b[], int off, int len)
        throws IOException {

    }
    */


    /**
     *
     */
    /*
    public long skip(long n)
        throws IOException {

    }
    */


    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking.
     */
    public int available()
        throws IOException {
        return (count - pos) + is.available();
    }


    /**
     * Close the input stream.
     */
    public void close()
        throws IOException {
        if (is == null)
            return;
        is.close();
        is = null;
        buf = null;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 填充字节流的内容到buf中
     * Fill the internal buffer using data from the undelying input stream.
     */
    protected void fill()
        throws IOException {
        pos = 0;
        count = 0;
        int nRead = is.read(buf, 0, buf.length);//每次读取都填满buf数组
        if (nRead > 0) {
            count = nRead;
        }
    }


}
