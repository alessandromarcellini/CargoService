package it.unibo.ioport;

public interface IDisplay {
	public void setState(String msg);
	public void setBookedSlot(int id);
	
	public String getState();
	public int getBookedSlotId();
	
}