package org.wso2.extensions.siddhi.io.file;

import org.wso2.carbon.messaging.BinaryCarbonMessage;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.CarbonMessageProcessor;
import org.wso2.carbon.messaging.ClientConnector;
import org.wso2.carbon.messaging.TransportSender;
import org.wso2.extensions.siddhi.io.file.utils.Constants;
import org.wso2.extensions.siddhi.io.file.utils.FileSourceConfiguration;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class  FileProcessor implements CarbonMessageProcessor {
    private CountDownLatch latch = new CountDownLatch(1);
    SourceEventListener sourceEventListener;
    FileSourceConfiguration fileSourceConfiguration;
    private String mode;
    private Pattern pattern;
    private Long filePointer;
    private StringBuilder sb = new StringBuilder();
    private String fileURI;



    public FileProcessor(SourceEventListener sourceEventListener, FileSourceConfiguration fileSourceConfiguration,
                          String fileURI) {
        this.sourceEventListener = sourceEventListener;
        this.fileSourceConfiguration = fileSourceConfiguration;
        this.mode = fileSourceConfiguration.getMode();
        this.fileURI = fileURI;
        configureFileMessageProcessor();
    }

    @Override
    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback) throws Exception {
        filePointer = fileSourceConfiguration.getFilePointer(fileURI);
        byte[] content = ((BinaryCarbonMessage) carbonMessage).readBytes().array();
        String msg = new String(content);

        if (Constants.TEXT_FULL.equalsIgnoreCase(mode)) {
            sourceEventListener.onEvent(new String(content));
            done();
        } else if (Constants.BINARY_FULL.equalsIgnoreCase(mode)) {
            //TODO : implement consuming binary files (file processor)
            sourceEventListener.onEvent(content);
            done();
        } else if (Constants.LINE.equalsIgnoreCase(mode)) {
            filePointer += content.length;
            fileSourceConfiguration.updateFilePointer(fileURI, filePointer);
            if(!fileSourceConfiguration.isTailingEnabled()) {
                InputStream is = new ByteArrayInputStream(content);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
                String line;
                while((line = bufferedReader.readLine()) != null){
                    sourceEventListener.onEvent(line.trim());
                }
                done();
            } else {
                sourceEventListener.onEvent(msg);
            }
        } else if(Constants.REGEX.equalsIgnoreCase(mode)){
            int  lastMatchIndex = 0;
            if(!fileSourceConfiguration.isTailingEnabled()) {
                char[] buf = new char[10];
                InputStream is = new ByteArrayInputStream(content);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

                while (bufferedReader.read(buf) != -1) {
                    sb.append(new String(buf));
                    Matcher matcher = pattern.matcher(sb.toString().trim());
                    while (matcher.find()) {
                        String event = matcher.group(0);
                        lastMatchIndex = matcher.end();
                        sourceEventListener.onEvent(event);
                    }
                    String tmp;
                    tmp = sb.substring(lastMatchIndex);

                    sb.setLength(0);
                    sb.append(tmp);
                }
                done();
            }else{
                sb.append(new String(content));
                Matcher matcher = pattern.matcher(sb.toString().trim());
                while (matcher.find()) {
                    String event = matcher.group(0);
                    lastMatchIndex = matcher.end();
                    sourceEventListener.onEvent(event);
                }
                String tmp;
                tmp = sb.substring(lastMatchIndex);

                sb.setLength(0);
                sb.append(tmp);
            }
        }
        return false;
    }

    @Override
    public void setTransportSender(TransportSender transportSender) {

    }

    @Override
    public void setClientConnector(ClientConnector clientConnector) {

    }

    @Override
    public String getId() {
        return null;
    }

    /**
     * To wait till file reading operation is finished.
     *
     * @throws InterruptedException Interrupted Exception.
     */
    public void waitTillDone() throws InterruptedException {
        latch.await();
    }

    /**
     * To make sure the reading the file content is done.
     */
    private void done() {
        latch.countDown();
    }

    private void configureFileMessageProcessor() {
        String beginRegex = fileSourceConfiguration.getBeginRegex();
        String endRegex = fileSourceConfiguration.getEndRegex();
        if (beginRegex != null && endRegex != null) {
            pattern = Pattern.compile(beginRegex + "(.+?)" + endRegex);
        } else if (beginRegex != null && endRegex == null) {
            pattern = Pattern.compile(beginRegex + "(.+?)" + beginRegex);
        } else if (beginRegex == null && endRegex != null) {
            pattern = Pattern.compile(".+?" + endRegex);
        }
    }
}
