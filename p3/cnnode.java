import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import sun.misc.*;

//import java.util.concurrent.atomic.AtomicBoolean;

public class cnnode {
        static <T extends Serializable> void sendPacket(T packet, InetAddress address, int port, DatagramSocket socket) throws Exception {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(packet);
                oos.flush();
                byte[] buffer = bos.toByteArray();
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(datagram);
        }

        static <T extends Serializable> T decodePacket(DatagramPacket datagram, Class<T> type) throws Exception {
                ByteArrayInputStream bis = new ByteArrayInputStream(datagram.getData());
                ObjectInputStream ois = new ObjectInputStream(bis);
                T packet = type.cast(ois.readObject());
                return packet;
        }

        static Object decodeObject(DatagramPacket packet) throws IOException, ClassNotFoundException {
                ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                return objectStream.readObject();
        }

        static int localSourcePort = 0;
        //static boolean sentOnce = false;
        //DVP Variables

        //all of the ports in PROBE_PORTS are in DV_UPDATE_PORTS, so checking
        //membership is O(1) using a set
        static ArrayList<Integer> DV_UPDATE_PORTS = new ArrayList<Integer>();
        static Set<Integer> PROBE_PORTS = new HashSet<Integer>();

        static Set<Integer> localTablePortSet = new HashSet<Integer>(); //stores ports in localTable for easy access        
        static HashMap<Integer, Tuple> localTable = new HashMap<Integer, Tuple>();

        static HashMap<Integer, ArrayList<Integer>> packet_lost_sent_table = new HashMap<Integer, ArrayList<Integer>>();
       
        static HashMap<Integer, Float> ideal_losses = new HashMap<Integer, Float>();


        static DatagramSocket sender = null;
        static DatagramSocket listenSocket = null;

        static class DVPacket implements Serializable {

                HashMap<Integer, Tuple> table;
                int srcPort; //sourcePort

                public DVPacket(HashMap<Integer, Tuple> table, int srcPort) {
                        this.table = table;
                        this.srcPort = srcPort;
                }

        }

        static class UpdateWeightPacket implements Serializable {

                int srcPort; float loss;
                public UpdateWeightPacket(int srcPort, float loss) {
                        this.srcPort = srcPort;
                        this.loss = loss;
                }
        }


        static class Tuple implements Serializable {
                int nextHop;
                float loss;

                public Tuple(int nextHop, float loss) {
                        this.nextHop = nextHop;
                        this.loss = loss;
                }
        }


        static class GoBackNPacket implements Serializable {
            int seqNum;
            int srcPort;
            float loss; //needed to simulate packet dropping from the sender's end.
            byte[] data;

            public GoBackNPacket(int seqNum, int srcPort, float loss, byte[] data) {
                this.seqNum = seqNum;
                this.srcPort = srcPort;
                this.loss = loss;
                this.data = data;
            }
        }

       static class GoBackNAck implements Serializable {
                int seqNum;

                GoBackNAck(int seqNum) {
                this.seqNum = seqNum;
                }
        }

        //Go-Back-N Variables
        static int nextSeqNum = 0; // sequence number of next packet to be sent
        static int base = 0; // sequence number of oldest unacknowledged packet
        static int expectedSeqNum = 0;
        static boolean receivedPacketSignal = false;

        public static void updateLoss() {
                for(Integer p: PROBE_PORTS) {
                        int packetsLost = packet_lost_sent_table.get(p).get(0);
                        int packetsSent = packet_lost_sent_table.get(p).get(1);
                        float direct_link_ratio = 0;
                        Date now = Calendar.getInstance().getTime();
                        if(packetsSent != 0) {
                                direct_link_ratio = (float) Math.round(100 * packetsLost/packetsSent) / 100;
                                if(direct_link_ratio != 0) {
                                localTable.put(p, new Tuple(p, direct_link_ratio));
                                }
                                System.out.println("["+now+"] Link to " + p + ": " + packetsSent + " packets sent, " + packetsLost + " packets lost, loss rate " + direct_link_ratio);
                        }
                        else {
                                System.out.println("["+now+"] Link to " + p + ": " + packetsSent + " packets sent, " + packetsLost + " packets lost, loss rate " + 0);
                        }
                }
        }

        public static void sendTable(InetAddress address) {
         
                for(Integer p: PROBE_PORTS) {
                        UpdateWeightPacket packet = new UpdateWeightPacket(localSourcePort, localTable.get(p).loss);
                        try {
                                sendPacket(packet, address, p, sender);
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.exit(1);
                        }
                }


                try {
                        Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                        e.printStackTrace();
                        System.exit(1);
                }


                      
               for(Integer key: DV_UPDATE_PORTS) {
                        DVPacket p = new DVPacket(localTable, localSourcePort);
                        try {
                                sendPacket(p, address, key, sender);
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.exit(1);
                        }
                } 

                try {
                        Thread.sleep(500);
                }
                catch (InterruptedException e) {
                        e.printStackTrace();
                        System.exit(1);
                }
/*
                Date now = Calendar.getInstance().getTime();
                System.out.println("[" + now + "] Node " + localSourcePort + " Routing Table");
                for (Integer key : localTable.keySet()) {
                       float loss = localTable.get(key).loss;
                       int nextHop = localTable.get(key).nextHop;
                       if(!key.equals(nextHop)) {
                               System.out.println("- (" + loss + ") -> Node " + key + "; Next hop -> Node " + nextHop);
                       }
                       else {
                               System.out.println("- (" + loss + ") -> Node " + key);
                      }
                }
                System.out.println("");
*/



        }

                public static void GBN(InetAddress address) {
                for(Integer receivingPort: PROBE_PORTS) {
                                String reset_signal = "reset";
                                GoBackNPacket reset = new GoBackNPacket(-1, localSourcePort, -1, reset_signal.getBytes());
                                try {
                                        sendPacket(reset,address,receivingPort,sender);
                                } catch (Exception e) {
                                        e.printStackTrace();
                                        System.exit(1);
                                }

                                String message = " "; //in this case, the message just needs to be a " "
                                // divide message into packets
                                List<GoBackNPacket> packets = new ArrayList<>();
                                int seqNum = 0;
                                for (int i = 0; i < message.length(); i++) {
                                        String data = message.substring(i, i + 1);
                                        packets.add(new GoBackNPacket(seqNum, localSourcePort, localTable.get(receivingPort).loss, data.getBytes()));
                                        seqNum++;
                                }

                                nextSeqNum = 0; // sequence number of next packet to be sent
                                base = 0; // sequence number of oldest unacknowledged packet
                                int windowSize = 5; //specified constant in the homework
                                // send packets to receiver
                                while(nextSeqNum < packets.size()) {
                                        Date now = Calendar.getInstance().getTime();
                                        while(nextSeqNum < base + windowSize && nextSeqNum < packets.size())
                                        {
                                                receivedPacketSignal = false;
                                                GoBackNPacket p = packets.get(nextSeqNum);
                                                try {
                                                        nextSeqNum++;
                                                        ArrayList<Integer> l = packet_lost_sent_table.get(receivingPort);
                                                        packet_lost_sent_table.put(receivingPort, new ArrayList<>(Arrays.asList(l.get(0), l.get(1) + 1)));
                                                        sendPacket(p,address,receivingPort,sender);
                                                } catch (Exception e) {
                                                        e.printStackTrace();
                                                        System.exit(1);
                                                }
                                        }

                                        try {
                                                Thread.sleep(500);
                                                if(receivedPacketSignal == false) {
                                                        nextSeqNum = base;
                                                        ArrayList<Integer> l = packet_lost_sent_table.get(receivingPort);
                                                        packet_lost_sent_table.put(receivingPort, new ArrayList<>(Arrays.asList(l.get(0) + 1, l.get(1))));
                                                }
                                        }
                                        catch (InterruptedException e) {
                                                // timeout expired, retransmit packets
                                                nextSeqNum = base;
                                        }
                                        nextSeqNum = base;
                                } //end of iterating through the packets
                        } //end of iterating through the ports
        }


        public static void clientListen(InetAddress address, int sourcePort) throws Exception {
                listenSocket = new DatagramSocket(sourcePort);

                boolean enableTableThread = true;
                
                //AtomicBoolean enableTableThread = new AtomicBoolean(true);

                boolean enableLossThread = true;
                boolean enableGBN = true;
                //enable multithreading here
                ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
                ExecutorService executeGBNMultithreadedService = Executors.newSingleThreadExecutor(); //for goback-n

                while (true) {

                       // receive incoming packet
                       byte[] buffer = new byte[1024];
                       DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                       listenSocket.receive(packet);
                       Object receivedObject = decodeObject(packet);


                        if(receivedObject instanceof DVPacket) {
                               DVPacket receivedPacket = decodePacket(packet, DVPacket.class);
                               boolean updatedTable = false;
                                //System.out.println("receivin DVP packet from " + receivedPacket.srcPort);
                                for (Integer key : receivedPacket.table.keySet()) {
                                  //      System.out.println("reviewing " + key + " from the receivedPacket");
                                /*        if(key == localSourcePort) {

                                                if( receivedPacket.table.get(localSourcePort).loss != localTable.get(receivedPacket.srcPort).loss) {
                                                        localTable.put(receivedPacket.srcPort, new Tuple(receivedPacket.srcPort, receivedPacket.table.get(key).loss));
                                                }
                                                localTablePortSet.add(key);
                                                continue;
                                        }
*/
                                        float option2;
                                        if(key == localSourcePort) continue;
                                        if (localTablePortSet.contains(key)) { //check whether the localTable has any information about this port

                                                        float option1 = localTable.get(key).loss;
                                                        option2 = localTable.get(receivedPacket.srcPort).loss + receivedPacket.table.get(key).loss;

//System.out.println(key + " " + option1 + " " + localTable.get(receivedPacket.srcPort).loss + " " + receivedPacket.table.get(key).loss);

                                                        if(option2 < option1 || (Math.abs(option1-option2) <= 0.02 && option1-option2 != 0f))  {
                                                                updatedTable = true;
                                                               //localTable.get(receivedPacket.srcPort).nextHop
                                //receivedPacket.srcPort
//System.out.println(key + " " + option1 + " " + localTable.get(receivedPacket.srcPort).loss + " " + receivedPacket.table.get(key).loss);
                                                                localTable.put(key, new Tuple(localTable.get(receivedPacket.srcPort).nextHop, option2));
                                                        }
                                        }
                                        else { //localTable doesn't have any information about this port.
                                                        updatedTable = true;

                                                        option2 = localTable.get(receivedPacket.srcPort).loss + receivedPacket.table.get(key).loss;
                                                       //System.out.println("adding information about " + key + ": " + option2);
                                                        localTable.put(key, new Tuple(receivedPacket.srcPort, option2));
                                                        localTablePortSet.add(key);

                                        }

                                }//end of for loop

                                if(updatedTable) {
                                        //DV_UPDATE_PORTS


                Date now = Calendar.getInstance().getTime();
                System.out.println("[" + now + "] Node " + localSourcePort + " Routing Table");
                for (Integer key : localTable.keySet()) {
                       float loss = localTable.get(key).loss;
                       int nextHop = localTable.get(key).nextHop;
                       if(!key.equals(nextHop)) {
                               System.out.println("- (" + loss + ") -> Node " + key + "; Next hop -> Node " + nextHop);
                       }
                       else {
                               System.out.println("- (" + loss + ") -> Node " + key);
                      }
                }
                System.out.println("");

                                        for(Integer key: DV_UPDATE_PORTS) {
                                                DVPacket p = new DVPacket(localTable, localSourcePort);
                                                try {
                                                        sendPacket(p, address, key, sender);
                                                } catch (Exception e) {
                                                        e.printStackTrace();
                                                        System.exit(1);
                                                }
                                        }


                                }

                        } else if(receivedObject instanceof UpdateWeightPacket) {
                                UpdateWeightPacket receivedPacket = decodePacket(
                                packet, UpdateWeightPacket.class);
                                //System.out.println("received updateweightpacket from: " + receivedPacket.srcPort);
                                if(!localTablePortSet.contains(receivedPacket.srcPort)) {
                                        localTablePortSet.add(receivedPacket.srcPort);
                                        localTable.put(receivedPacket.srcPort, new Tuple(receivedPacket.srcPort, receivedPacket.loss));
                                }
                                else {
                                                //localTable.get(receivedPacket.srcPort).netHop
                                        //receivedPacket.srcPort
                                        localTable.put(receivedPacket.srcPort, new Tuple(receivedPacket.srcPort, receivedPacket.loss));
                                }
                        }
                        else if(receivedObject instanceof GoBackNPacket) {
                                GoBackNPacket receivedPacket = decodePacket(packet, GoBackNPacket.class);

                                if(receivedPacket.seqNum == -1) {
                                        expectedSeqNum = 0;
                                        continue;
                                }

                                Random random = new Random();
                                float randomFloat = random.nextFloat();
                                float idealLoss = ideal_losses.get(receivedPacket.srcPort);

                                if(randomFloat < idealLoss) {
                                        continue;
                                }

                                if(receivedPacket.seqNum == expectedSeqNum) {
                                        GoBackNAck ack = new GoBackNAck(expectedSeqNum);
                                        expectedSeqNum++;
                                        sendPacket(ack, address, receivedPacket.srcPort, sender);
                                } else if(receivedPacket.seqNum < expectedSeqNum) {
                                        GoBackNAck ack = new GoBackNAck(expectedSeqNum - 1);
                                        sendPacket(ack, address, receivedPacket.srcPort, sender);
                                } else {
                                        GoBackNAck ack = new GoBackNAck(expectedSeqNum - 1);
                                        sendPacket(ack, address, receivedPacket.srcPort, sender);
                                }

                       } else if(receivedObject instanceof GoBackNAck) {
                                GoBackNAck ack = decodePacket(packet, GoBackNAck.class);

                                if(ack.seqNum >= base) {
                                        base = ack.seqNum + 1;
                                        receivedPacketSignal = true;
                                }
                       }


                      if(enableLossThread) {
                              executorService.scheduleAtFixedRate(cnnode::updateLoss, 0, 1, TimeUnit.SECONDS);
                              enableLossThread = false;
                      }

                      //enabletablethread.get()
                      if(enableTableThread) {
                                executorService.scheduleAtFixedRate(() -> sendTable(address), 0, 5, TimeUnit.SECONDS);
                                enableTableThread = false;
                     
 /*   executorService.schedule(() -> {
        executorService.scheduleAtFixedRate(() -> sendTable(address), 0, 5, TimeUnit.SECONDS);
        enableTableThread.set(false);
    }, 60, TimeUnit.SECONDS);
*/
                      }

                        if(enableGBN) {

                                enableGBN = false;

                                try {
                                        Thread.sleep(1000);
                                }
                                catch (InterruptedException e) {
                                        // timeout expired, retransmit packets
                                        e.printStackTrace();
                                        System.exit(1);
                                }

                                executeGBNMultithreadedService.submit(() -> {
                                        while(true) {
                                        GBN(address);
                                       }
                                });
                        }


                } //end of while true
        } //end of function


         public static void clientMode(int sourcePort, boolean beginSendingPackets) throws Exception {

                try {
                       sender = new DatagramSocket();
                } catch (SocketException e) {
                       System.err.println("Error creating socket: " + e.getMessage());
                }


                final InetAddress address = InetAddress.getLoopbackAddress();
                try {
                } catch (Exception e) {
                        System.err.println("Unknown host: " + e.getMessage());
                        System.exit(1);
                }

                Thread listen = new Thread(new Runnable() {
                @Override
                public void run() {
                        try {
                                clientListen(address, sourcePort);
                        }
                        catch (Exception e) {
                                e.printStackTrace();
                                System.exit(1);
                        }
                }
                });

                listen.start();
                if(beginSendingPackets) {
                        //dv_update_ports
                        Date now = Calendar.getInstance().getTime();
                        for(Integer key: DV_UPDATE_PORTS) {
                                //sourcePort->localSourcePort
                                DVPacket p = new DVPacket(localTable, localSourcePort);
                                try {
                                        sendPacket(p,address,key,sender);
                                } catch (Exception e) {
                                        e.printStackTrace();
                                        System.exit(1);
                                }
                       }

                }

     }

       //SETS UP THE PARAMETERS
        public static void main(String[] args) {

                if(args.length == 0) {
                System.out.println("java cnnode <local-port> receive <neighbor1-port> <loss-rate-1> <neighbor2-port> <loss-rate-2> ... <neighborM-port> <loss-rate-M> send <neighbor(M+1)-port> <neighbor(M+2)-port> ... <neighborN-port> [last]");
                System.exit(1);
                }

                localSourcePort = Integer.parseInt(args[0]);
                if(localSourcePort < 1024 || localSourcePort > 65534) {
                        System.out.println("Source port should be in between 1024 and 65534");
                        System.exit(1);
                }
                boolean lastPresent = false;
                //localTable.put(localSourcePort, new Tuple(localSourcePort, 0f));
                //localTablePortSet.add(localSourcePort);
                try {
                        int i = 1;
                        while(!args[i].equals("send")) {
                                if(args[i].equals("receive")) i++;
                                else {
                                        int neighborPort = Integer.parseInt(args[i]);
                                        if(neighborPort < 1024 || neighborPort > 65534) {
                                                System.out.println("Neighbor port should be in between 1024 and 65534");
                                                System.exit(1);
                                        }

                                        float lossRate = Float.parseFloat(args[i+1]);
                                        if(lossRate < 0 || lossRate > 1) {
                                                System.out.println("Loss rate should be in between 0 and 1.");
                                        }
                                        localTable.put(neighborPort, new Tuple(neighborPort, Float.MAX_VALUE));
                                        ideal_losses.put(neighborPort, lossRate);
                                        localTablePortSet.add(neighborPort); //stores ports found in localTable for easy access
              //                          packet_lost_sent_table.put(neighborPort, new ArrayList<>(Arrays.asList(0,0)));
                                        DV_UPDATE_PORTS.add(neighborPort);
                                        i+=2;
                                }
                        }

                        while(i < args.length) {
                                if(args[i].equals("send")) i++;
                                else if(args[i].equals("last")) break;
                                else {
                                        int neighborPort = Integer.parseInt(args[i]);
                                        if(neighborPort < 1024 || neighborPort > 65534) {
                                                System.out.println("Neighbor port should be in between 1024 and 65534");
                                                System.exit(1);
                                        }
                                        localTable.put(neighborPort, new Tuple(neighborPort, Float.MAX_VALUE));
                                        localTablePortSet.add(neighborPort);
                                        DV_UPDATE_PORTS.add(neighborPort);
                                        PROBE_PORTS.add(neighborPort);
                                        packet_lost_sent_table.put(neighborPort, new ArrayList<>(Arrays.asList(0,0)));
                                        i++;
                                }
                        }
                } catch(NumberFormatException e) {
                        System.err.println("Invalid argument: " + e.getMessage());
                        System.exit(1);
                }

                if(args[args.length - 1].equals("last")) {
                        lastPresent = true;
                }


                try {
                        clientMode(localSourcePort, lastPresent);
                }
                catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                }

        }     //end of void main function
} //end of dvnode class
