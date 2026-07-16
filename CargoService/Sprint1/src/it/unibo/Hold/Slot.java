package it.unibo.Hold;

public class Slot implements ISlot {

    private final int id;
    private IContainer content;

    public Slot(int id) {
        this.id = id;
        this.content = null;
    }

    @Override
    public CellType getType() {
        return CellType.SLOT;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public IContainer getContent() {
        return this.content;
    }

    @Override
    public void setContent(IContainer container) {
        this.content = container;
    }
}