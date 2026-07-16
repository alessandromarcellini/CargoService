package it.unibo.ioport;

public class Display implements IDisplay {
	
	private String currStateMsg = "";
	private int currBookedSlotId = -1;
	
	
	@Override
	public void setState(String msg) {
		this.currStateMsg = msg;
	}

	@Override
	public void setBookedSlot(int id) {
		this.currBookedSlotId = id;
	}

	@Override
	public String getState() {
		return this.currStateMsg;
	}

	@Override
	public int getBookedSlotId() {
		return this.currBookedSlotId;
	}

}
