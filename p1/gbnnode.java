
public class gbnnode {
	public static void main(String[] args) {

        int selfPort = -1;
        int peerPort = -1;
        int windowSize = -1;
        int n = Integer.MIN_VALUE;
        float p = Float.MIN_VALUE;

	try {
        	for (int i = 0; i < args.length; i++) {
            	if (i == 0) {
                	selfPort = Integer.parseInt(args[i]);
            	} else if (i == 1) {
                	peerPort = Integer.parseInt(args[i]);
            	} else if (i == 2) {
                	windowSize = Integer.parseInt(args[i]);
            	} else if (args[i].equals("-d") && i+1 < args.length) {
			n = Integer.parseInt(args[i+1]);
            	} else if (args[i].equals("-p") && i+1 < args.length) {
			p = Float.parseFloat(args[i+1]);
            	} else if(i >= 7) {
                        System.out.println("Follow format: java gbnnode <self-port> <peer-port> <window-size> [ -d <value-of-n> | -p <value-of-p>]");
                        System.exit(1);
		}
		  
        	} 
	} catch(NumberFormatException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            System.exit(1);
	}
		if(selfPort == -1 || peerPort == -1 || windowSize == -1) {
			System.out.println("Follow format: java gbnnode <self-port> <peer-port> <window-size> [ -d <value-of-n> | -p <value-of-p>]");
			System.exit(1);
		} else if((p < 0 || p > 1) && p != Float.MIN_VALUE ) {
			System.out.println("p must be between 0 and 1");
			System.exit(1);
		} else if(n <= 0 && n != Integer.MIN_VALUE) {
			System.out.println("n must be >= 1");
			System.exit(1);
		}

	gbnsender sender = new gbnsender();
	sender.main(selfPort, peerPort, windowSize, n, p);
    
	}

}



























