package it.unibo.Hold;

public interface ISlot extends ICell {
	public int getId(); // from 1 to 5
	
	public IContainer getContent();
	public void setContent(IContainer container);
}