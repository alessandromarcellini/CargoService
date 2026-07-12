package it.unibo.Hold;

public interface IHold {
	
	public ICell[][] getHold();
	public ICell getCell(int i, int j);
	
	public HoldState getState();
	public void setState(HoldState state);
	public void setCell(int i, int j, ICell cell);
	
	public ISlot nextFreeSlot(); // if returns null, all slots are taken
}