package it.unibo.Hold;

public class Slot extends Cell implements ISlot {

    private final int id;
    private IContainer content;

    // Aggiornato il costruttore per ricevere anche x e y
    public Slot(int id, int x, int y) {
        super(CellType.SLOT, x, y);
        this.id = id;
        this.content = null;
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