package it.unibo.Hold;

public class Cell implements ICell {

    private final CellType type;
    private int x;
    private int y;

    public Cell(CellType type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    @Override
    public CellType getType() {
        return this.type;
    }

	@Override
	public int getX() {
		return this.x;
	}

	@Override
	public int getY() {
		return this.y;
	}
}