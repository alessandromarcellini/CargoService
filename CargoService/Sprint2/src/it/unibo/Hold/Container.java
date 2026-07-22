package it.unibo.Hold;

public class Container implements IContainer {
	
	String barcode = "";
	int length = 8;
	int width = 2;

	@Override
	public String getBarcode() {
		return this.barcode;
	}

	@Override
	public int getLength() {
		return this.length;
	}

	@Override
	public int getWidth() {
		return this.width;
	}
	
}