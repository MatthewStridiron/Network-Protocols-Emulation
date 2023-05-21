import java.net.*;
import java.util.*;
import java.io.*;

public class gbnsender {
	static DatagramSocket sender = null;
        static int nextSeqNum = 0; // sequence number of next packet to be sent
        static int base = 0; // sequence number of oldest unacknowledged packet
	static int expectedSeqNum = 0;

	static int discardReceiverCounter = 0;
	static int discardSenderCounter = 0;

	static boolean receivedPacketSignal = false;

	static int packetsSenderDropped = 0;
	static int packetsReceiverDropped = 0;

	static void sendPacket(Packet packet, InetAddress receiverAddress, int receiverPort, DatagramSocket socket) throws Exception {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(packet);
                oos.flush();
                byte[] buffer = bos.toByteArray();
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, receiverAddress, receiverPort);
                socket.send(datagram);
        }

	//main difference between these two functions is the first parameter
	static void sendAckPacket(AckPacket ack, InetAddress senderAddress, int senderPort, DatagramSocket socket) throws Exception {
    		ByteArrayOutputStream bos = new ByteArrayOutputStream();
    		ObjectOutputStream oos = new ObjectOutputStream(bos);
    		oos.writeObject(ack);
    		oos.flush();
    		byte[] buffer = bos.toByteArray();
    		DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, senderAddress, senderPort);
    		socket.send(datagram);
	}

	static Object decodeObject(DatagramPacket packet) throws IOException, ClassNotFoundException {
    		ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
    		ObjectInputStream objectStream = new ObjectInputStream(byteStream);
    		return objectStream.readObject();
	}

        static Packet decodePacket(DatagramPacket datagram) throws Exception {
                ByteArrayInputStream bis = new ByteArrayInputStream(datagram.getData());
                ObjectInputStream ois = new ObjectInputStream(bis);
                Packet packet = (Packet) ois.readObject();
                return packet;
        }

        static AckPacket decodeAckPacket(DatagramPacket datagram) throws Exception {
                ByteArrayInputStream bis = new ByteArrayInputStream(datagram.getData());
                ObjectInputStream ois = new ObjectInputStream(bis);
                AckPacket ack = (AckPacket) ois.readObject();
                return ack;
        }

	// packet class to represent a packet
	static class Packet implements Serializable {
	    int seqNum;
	    byte[] data;

	    public Packet(int seqNum, byte[] data) {
        	this.seqNum = seqNum;
        	this.data = data;
    	    }
    	
	    public int getSeqnum() {
        	return seqNum;
    	    }

    	    public byte[] getData() {
        	return data;
    	    }
	}

	// packet class to represent an ACK packet
	static class AckPacket implements Serializable {
    		int seqNum;

    		AckPacket(int seqNum) {
        	this.seqNum = seqNum;
    		}
	}


	public static void clientListen(InetAddress address, int initialSenderPort, int initialReceivingPort, int windowSize, int dropRate, float dropProbability) throws Exception {
        	DatagramSocket listenSocket = new DatagramSocket(initialSenderPort);
    
    		// expected sequence number of next packet
    		//int expectedSeqNum = 0;
		while (true) {
        		// receive incoming packet
        		byte[] buffer = new byte[1024];
        		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        		listenSocket.receive(packet);

			Object receivedObject = decodeObject(packet);
			Date now = Calendar.getInstance().getTime();
			if(receivedObject instanceof Packet) {				
				// decode incoming packet
        			Packet receivedPacket = decodePacket(packet);

				if(receivedPacket.seqNum == -1) {
				       	expectedSeqNum = 0;
					packetsReceiverDropped = 0;
					discardReceiverCounter = 0;
					//ideally, we should send an ACK back. check with TA about whether this implementation is acceptable
				} else if(receivedPacket.seqNum == -2) {
                        		float ratio = (float) packetsReceiverDropped/discardReceiverCounter;
                        		System.out.println("[Summary] " + packetsReceiverDropped + "/" + discardReceiverCounter + " total packets discarded, loss rate = " + ratio);
				}
				else 	{
					discardReceiverCounter++;
//					System.out.println("discardReceiverCounter: " + discardReceiverCounter);
					if(discardReceiverCounter % dropRate == 0 && dropRate >= 1 && discardReceiverCounter != 0) 
					{
						System.out.println("["+now+"] packet"+receivedPacket.seqNum+ " " + new String(receivedPacket.data)+" discarded");
						packetsReceiverDropped++;
						continue;
					}

					Random random = new Random();
        				float randomFloat = random.nextFloat();
					//System.out.println(randomFloat);
					if(randomFloat < dropProbability && dropProbability != -1) {
                                                System.out.println("["+now+"] packet"+receivedPacket.seqNum+ " " + new String(receivedPacket.data)+" discarded");
						packetsReceiverDropped++;
						continue;
					}

					System.out.println("["+now+"] packet"+receivedPacket.seqNum+" " + new String(receivedPacket.data)+" received");

     					// check sequence number of received packet
        				if (receivedPacket.seqNum == expectedSeqNum) {
            					// in-sequence packet received, add to buffer and send ACK
						AckPacket ack = new AckPacket(expectedSeqNum);
            					System.out.println("["+now+"] ACK" + expectedSeqNum + " sent, expecting packet" + Integer.toString(expectedSeqNum+1));
						expectedSeqNum++;
						sendAckPacket(ack, address, initialReceivingPort, sender); 
            					//expectedSeqNum++;
        				} else if (receivedPacket.seqNum < expectedSeqNum) {
            					// duplicate packet received, send ACK for last in-sequence packet
            					AckPacket ack = new AckPacket(expectedSeqNum - 1);
            					sendAckPacket(ack, address, initialReceivingPort, sender);
            					System.out.println("["+now+"] Received duplicate packet " + receivedPacket.seqNum + " and sent ACK " + (expectedSeqNum - 1));
        				} else {
            					// out-of-sequence packet received, discard and send ACK for last in-sequence packet
            					AckPacket ack = new AckPacket(expectedSeqNum - 1);
            					sendAckPacket(ack, address, initialReceivingPort, sender);
            					System.out.println("["+now+"] Received out-of-sequence packet " + receivedPacket.seqNum + " and sent ACK " + (expectedSeqNum - 1));
        				}
				}
			} else if(receivedObject instanceof AckPacket) { //mailed to the sender
                        	try {
					discardSenderCounter++;
                                	AckPacket ack = decodeAckPacket(packet);
                                	
                                        if(discardSenderCounter % dropRate == 0 && dropRate >= 1 && discardSenderCounter != 0) {
                                                System.out.println("["+now+"] ACK"+ack.seqNum+ " discarded");
						packetsSenderDropped++;
						continue;
                                        }

                                        Random random = new Random();
                                        float randomFloat = random.nextFloat();

                                        if(randomFloat < dropProbability && dropProbability != -1) {
                                                System.out.println("["+now+"] ACK"+ack.seqNum+ " discarded");
                                                packetsSenderDropped++;
						continue;
                                        }
					//System.out.println("ack.seqNum: " + ack.seqNum);		
                                	if (ack.seqNum >= base) {
						System.out.println("["+now+"] ACK" + ack.seqNum + " received, window moves to " + Integer.toString(ack.seqNum + 1));
                                        	base = ack.seqNum + 1;
						//System.out.println("new base: " + base);
						receivedPacketSignal = true;
                                	} else {
						System.out.println("["+now+"] Duplicate ACK"+ack.seqNum+" received.");
					}
					//receivedPacketSignal = true;
                        	} catch (IOException e) {
                                	System.err.println("Error receiving ACK: " + e.getMessage());
                                	System.exit(1);
                        	} catch (Exception e) {
                                	e.printStackTrace();
                                	System.exit(1);
                        	}
			}
    		} //end of while true statement
	} //end of function call


	public static void main(final int sendingPort, final int receivingPort, final int windowSize, final int dropPackets, final float p) {

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
            				clientListen(address, sendingPort, receivingPort, windowSize, dropPackets, p);
        			}  
				catch (Exception e) {
        				e.printStackTrace();
        			}	
    			}
		});

		listen.start();

		while(true) {	
		// read input command from user
		String reset_signal = "reset";
		Packet reset = new Packet(-1, reset_signal.getBytes());
		try {
			sendPacket(reset,address,receivingPort,sender);		
		} catch (Exception e) {
                        e.printStackTrace();
                	System.exit(1);
                }

		Scanner scanner = new Scanner(System.in);
		System.out.print("Enter command (send <message>): ");
		String command = scanner.nextLine();
		while(command.length() < 5 || !command.substring(0, 4).equals("send")) {
			System.out.print("Enter command (send <message>): ");
                	command = scanner.nextLine();	
		}

		String message = command.substring(5);

		// divide message into packets
		List<Packet> packets = new ArrayList<>();
		int seqNum = 0;
		for (int i = 0; i < message.length(); i++) {
    			String data = message.substring(i, i + 1);
    			packets.add(new Packet(seqNum, data.getBytes()));
    			seqNum++;
		}
		
                nextSeqNum = 0; // sequence number of next packet to be sent
                base = 0; // sequence number of oldest unacknowledged packet
		packetsSenderDropped = 0;
		discardSenderCounter = 0;
        	// send packets to receiver
		while (nextSeqNum < packets.size()) {
        	//while (base < packets.size() && nextSeqNum < packets.size()) { //make sure you iterate over all of the packets
		
            	// send packets up to the window size, or to the length of the packet
			//System.out.println("base: " + base);
			//System.out.println("next seq num: " +  nextSeqNum);	
			Date now = Calendar.getInstance().getTime();
			while (nextSeqNum < base + windowSize && nextSeqNum < packets.size())
			//while (nextSeqNum < base + windowSize && base < packets.size() && nextSeqNum < packets.size()) 
			{ 
			
                        	receivedPacketSignal = false;
                        	Packet packet = packets.get(nextSeqNum);

    				try {
					System.out.println("["+now+"] packet"+base+" " +new String(packet.data)+" sent");
        				nextSeqNum++;
					sendPacket(packet,address,receivingPort,sender);
    				} catch (Exception e) {
        				e.printStackTrace();
					System.exit(1);
    				}
				//System.out.println("Sending packet.");	
				//System.out.println("base, nextSeqNum: " + base + " " + nextSeqNum);
            		}
			//System.out.println("will now wait 500ms");
			
			try {
				Thread.sleep(500);
				if(receivedPacketSignal == false) {
					nextSeqNum = base;
					System.out.println("["+now+"] packet"+base+" timeout");
					//System.out.println("base, nextSeqNum: " + base + " " + nextSeqNum);
				}
			} 
                        catch (InterruptedException e) {
                        	// timeout expired, retransmit packets
                                System.out.println("Timeout expired, retransmitting packets " + base + " to " + (nextSeqNum - 1));
                                nextSeqNum = base;
                        } 		

			nextSeqNum = base;
	
		} //end of iterating through the packets
			
			float ratio = (float) packetsSenderDropped/discardSenderCounter;
			System.out.println("[Summary] " + packetsSenderDropped + "/" + discardSenderCounter + " total packets discarded, loss rate = " + ratio);

                	String receiver_loss_ratio_signal = "receiver_loss_ratio_signal";
                	Packet finalCall = new Packet(-2, receiver_loss_ratio_signal.getBytes());

                	try {
                        	sendPacket(finalCall,address,receivingPort,sender);
                	} catch (Exception e) {
                        	e.printStackTrace();
                        	System.exit(1);
                	}

		} //end of while true loop

	} //end of main function
} //end of class


