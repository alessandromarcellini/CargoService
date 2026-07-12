package it.unibo.Hold;

public class Cell implements ICell {

    private final CellType type;

    public Cell(CellType type) {
        this.type = type;
    }

    @Override
    public CellType getType() {
        return this.type;
    }
}