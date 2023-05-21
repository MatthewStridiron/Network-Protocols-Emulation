import java.net.*;
import java.util.*;
import java.io.*;
import sun.misc.*;

public class dvnode {

	static int sourcePort = 0;
	static HashMap<Integer, Tuple> localTable = new HashMap<Integer, Tuple>();
	static DatagramSocket sender = null;
	static Set<Integer> ports_visited_set = new HashSet<Integer>();	//used to track all of the ports visited
	static Set<Integer> neighboringPortsSet = new HashSet<Integer>(); //used to access neighboring ports in o(1) time

	static class Packet implements Serializable {

		HashMap<Integer, Tuple> table;	
		int srcPort; //sourcePort

		public Packet(HashMap<Integer, Tuple> table, int srcPort) {
			this.table = table;
			this.srcPort = srcPort;
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

        static void sendPacket(Packet packet, InetAddress receiverAddress, int receiverPort, DatagramSocket socket) throws Exception {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(packet);
                oos.flush();
                byte[] buffer = bos.toByteArray();
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, receiverAddress, receiverPort);
                socket.send(datagram);
        }


        static Packet decodePacket(DatagramPacket datagram) throws Exception {
                ByteArrayInputStream bis = new ByteArrayInputStream(datagram.getData());
                ObjectInputStream ois = new ObjectInputStream(bis);
                Packet packet = (Packet) ois.readObject();
                return packet;
        }

        public static void clientListen(InetAddress address, int sourcePort) throws Exception {
                DatagramSocket listenSocket = new DatagramSocket(sourcePort);
		boolean beforeConvergence = true;

                while (true) {
                
                        // receive incoming packet

                        byte[] buffer = new byte[1024];
                        boolean tableUpdated = false;
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        listenSocket.receive(packet);

                        Packet receivedPacket = decodePacket(packet);
				//System.out.println("Received table. Going through ports in the receiving table.");
                		for (Integer key : receivedPacket.table.keySet()) { //DVP = iterate over the ports in the receivedTable
                        		//System.out.println(key);
					if (ports_visited_set.contains(key)) {
						//System.out.println("I've seen this port before and added it to the table.");
						if(key == sourcePort) continue; //self cycle
                                			else {
                                        			try {
									//try: 4444->1111.
                                                			float option1 = localTable.get(key).loss;
                                                			float option2 = localTable.get(receivedPacket.srcPort).loss + receivedPacket.table.get(key).loss;
                                                			if(option2 < option1) {
										//System.out.println("key: " + key);
										//System.out.println("option 1: " + option1 + " option 2: " + option2);
										//System.out.println("option2 components: " + localTable.get(receivedPacket.srcPort).loss + " and " + receivedPacket.table.get(key).loss);
										//System.out.println("srcPort of packet: " + receivedPacket.srcPort);
										//System.out.println("Better link found. Updating table.");
										//System.out.println("nexthop: " + localTable.get(key).nextHop + " route: " + option2); 
                                                        			localTable.put(key, new Tuple(localTable.get(receivedPacket.srcPort).nextHop, option2));
										tableUpdated = true;
										//localTable.get(key).nextHop
                                                			} 
                                        			} catch(Exception e) {
                                                			e.printStackTrace();
                                                			sender.close();
                                                			listenSocket.close();
                                        			}
                                			}
                        		} else {
                                		try {
							//receivedPacket.port
							//System.out.println("Never seen this port before. Adding it to the table for the first time.");
                                        		float option2 = localTable.get(receivedPacket.srcPort).loss + receivedPacket.table.get(key).loss;
                                        		localTable.put(key, new Tuple(receivedPacket.srcPort, option2));
                                        		ports_visited_set.add(key);
							tableUpdated = true;
                                		} catch(Exception e) {
                                        		e.printStackTrace();
                                        		sender.close();
                                        		listenSocket.close();
                                		}
                        		}
                		}

				if(beforeConvergence || tableUpdated) {
                                	beforeConvergence = false;
					Date now = Calendar.getInstance().getTime();
                                	System.out.println("[" + now + "] Node " + sourcePort + " Routing Table");
                                	for (Integer key : localTable.keySet()) {
	                                	float loss = localTable.get(key).loss;
						int nextHop = localTable.get(key).nextHop;
						if(!key.equals(nextHop)) {
                                        		System.out.println("- (" + loss + ") -> Node " + key + "; Next hop -> Node " + nextHop);
						}
						else {
							System.out.println("- (" + loss + ") -> Node " + key);	
						}

						if(neighboringPortsSet.contains(key)) {	//broadcast to neighboring ports
                                        		Packet p = new Packet(localTable, sourcePort);
                                        		try {
                                        			sendPacket(p, address, key, sender);
                                        		} catch (Exception e) {
                                        			e.printStackTrace();
                                        			System.exit(1);
                                        		}
						}
					}
					System.out.println("");
				} 

                }
        }  

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
                        Date now = Calendar.getInstance().getTime();
                        //System.out.println("["+now+"] Node " + sourcePort + " Routing Table");
                        for(Integer key: localTable.keySet()) {

                                //float loss = localTable.get(key).loss;
                                //System.out.println("- ("+loss+") -> Node " + key);

                                Packet p = new Packet(localTable, sourcePort);
                                try {
                                        sendPacket(p,address,key,sender);
                                } catch (Exception e) {
                                        e.printStackTrace();
                                        System.exit(1);
                                }
                       }
                       //System.out.println("");

                }
	
}
        
     public static void main(String[] args) {

	if(args.length == 0) {
		System.out.println("Please use the following format: java dvnode <local-port> <neighbor1-port> <loss-rate-1> <neighbor2-port> <loss-rate-2> ... [last]");
		System.exit(1);
	}
	
	sourcePort = Integer.parseInt(args[0]);
	if(sourcePort < 1024 || sourcePort > 65534) {
		System.out.println("Source port should be in between 1024 and 65534");
		System.exit(1);
	}
	
	ports_visited_set.add(sourcePort);
	boolean lastPresent = false;

        try {
                for (int i = 1; i < args.length - 1; i+=2) {
			int neighborPort = Integer.parseInt(args[i]);
			if(neighborPort < 1024 || neighborPort > 65534) {
				System.out.println("Neighbor port should be in between 1024 and 65534");
                		System.exit(1);
        		}	
			ports_visited_set.add(neighborPort);
			neighboringPortsSet.add(neighborPort);
			float lossRate = Float.parseFloat(args[i+1]);
			if(lossRate < 0 || lossRate > 1) {
				System.out.println("Loss rate should be in between 0 and 1.");
			}
			localTable.put(neighborPort, new Tuple(neighborPort, lossRate));
		} 
        } catch(NumberFormatException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            System.exit(1);
        }
	
	if(args[args.length - 1].equals("last")) {
		lastPresent = true;
	}


	try {
		clientMode(sourcePort, lastPresent);
        }
        catch (Exception e) {
        	e.printStackTrace();
                System.exit(1);
        }
  
	}     //end of void main function
} //end of dvnode class





























