import org.wso2.carbon.databridge.agent.AgentHolder;
import org.wso2.carbon.databridge.agent.DataPublisher;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAgentConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAuthenticationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointException;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.commons.exception.TransportException;
import org.wso2.carbon.databridge.commons.utils.DataBridgeCommonsUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ThriftPublisher implements Runnable {
    private static final String DATA_STREAM = "SmartPlugsDataStream";
    private static final String VERSION = "1.0.0";
    private static final int defaultThriftPort = 30078;
    private static final int defaultBinaryPort = 9611;
    private String host;
    private String dataFileLocation;

    public ThriftPublisher(String host, String dataFileLocation) {
        this.host = host;
        this.dataFileLocation = dataFileLocation;
    }

    @Override
    public void run() {
        try {
            publish();
        } catch (DataEndpointAuthenticationException e) {
            e.printStackTrace();
        } catch (DataEndpointAgentConfigurationException e) {
            e.printStackTrace();
        } catch (TransportException e) {
            e.printStackTrace();
        } catch (DataEndpointException e) {
            e.printStackTrace();
        } catch (DataEndpointConfigurationException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public String getDataAgentConfigPath() {
        File filePath = new File("src" + File.separator + "main" + File.separator + "resources");
        if (!filePath.exists()) {
            filePath = new File("test" + File.separator + "resources");
        }
        if (!filePath.exists()) {
            filePath = new File("resources");
        }
        if (!filePath.exists()) {
            filePath = new File("test" + File.separator + "resources");
        }
        return filePath.getAbsolutePath() + File.separator + "data-agent-conf.xml";
    }

    private void publishLogEvents(DataPublisher dataPublisher, String streamId, String dataFilePath) throws FileNotFoundException {
        Scanner scanner = new Scanner(new FileInputStream(dataFilePath));
        int i = 1;
        while (scanner.hasNextLine()) {
            // System.out.println(Thread.currentThread().getName() + " : Publish streaming event : " + i);
            String anEntry = scanner.nextLine();
            String[] separatedEntries = anEntry.split(",");
            Object[] payload = new Object[]{
                    Long.parseLong(separatedEntries[0]), Long.parseLong(separatedEntries[1]),
                    Float.parseFloat(separatedEntries[2]), Integer.parseInt(separatedEntries[3]),
                    separatedEntries[4], separatedEntries[5], separatedEntries[6]
            };
            Event event = new Event(streamId, System.currentTimeMillis(), new Object[]{"electricityConsumption"}, null,
                    payload);
            dataPublisher.publish(event);
            ThriftClient.COUNT++;
            if (ThriftClient.COUNT % 100000 == 0) {
                System.out.println("Events Published : " + ThriftClient.COUNT);
            }

            i++;
        }
        scanner.close();
    }

    private String getProperty(String name, String def) {
        String result = System.getProperty(name);
        if (result == null || result.length() == 0 || "".equals(result)) {
            result = def;
        }
        return result;
    }

    public void publish() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException,
            TransportException,
            DataEndpointException,
            DataEndpointConfigurationException,
            FileNotFoundException {

        AgentHolder.setConfigPath(getDataAgentConfigPath());
        String type = getProperty("type", "Thrift");
        int receiverPort = defaultThriftPort;
        if (type.equals("Binary")) {
            receiverPort = defaultBinaryPort;
        }
        int securePort = receiverPort + 1;

        String url = getProperty("url", "tcp://" + host + ":" + receiverPort);
        String authURL = getProperty("authURL", "ssl://" + host + ":" + securePort);
        String username = getProperty("username", "admin");
        String password = getProperty("password", "admin");

        DataPublisher dataPublisher = new DataPublisher(type, url, authURL, username, password);
        String streamId = DataBridgeCommonsUtils.generateStreamId(DATA_STREAM, VERSION);
        publishLogEvents(dataPublisher, streamId, this.dataFileLocation);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        dataPublisher.shutdown();
    }
}
