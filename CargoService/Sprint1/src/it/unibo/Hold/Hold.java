package it.unibo.Hold;

public class Hold implements IHold {
	
	private ICell[][] hold = new ICell[7][7];
	private HoldState currentState = HoldState.DISENGAGED;

	public Hold() {

	    for (int i = 0; i < 7; i++) {
	        for (int j = 0; j < 7; j++) {
	            hold[i][j] = new Cell(CellType.FREE);
	        }
	    }

	    // HOME
	    hold[0][0] = new Cell(CellType.HOME);

	    // SLOT
	    hold[2][1] = new Slot(1);
	    hold[5][1] = new Slot(2);
	    hold[2][3] = new Slot(3);
	    hold[5][3] = new Slot(4);
	    hold[6][2] = new Slot(5);

	    // OSTACOLI
	    hold[3][1] = new Cell(CellType.OBSTACLE);
	    hold[4][1] = new Cell(CellType.OBSTACLE);
	    hold[3][3] = new Cell(CellType.OBSTACLE);
	    hold[4][3] = new Cell(CellType.OBSTACLE);
	    hold[5][2] = new Cell(CellType.OBSTACLE);

	    // IOPORT
	    hold[1][6] = new Cell(CellType.IOPORT);
	}

	@Override
	public ICell[][] getHold() {
		return this.hold;
	}

	@Override
	public ICell getCell(int i, int j) {
		return this.hold[i][j];
	}

	@Override
	public HoldState getState() {
		return this.currentState;
	}

	@Override
	public void setState(HoldState state) {
		this.currentState = state;
		
	}

	@Override
	public void setCell(int i, int j, ICell cell) {
		this.hold[i][j] = cell;		
	}
	
	
	public ISlot nextFreeSlot() {
		// Returns the first storage slot (id 1..4) that is currently free
		// (no container placed), or null if all four are occupied.
		// Slot 5 is temporary marking storage, not a reservable destination,
		// so it is excluded on purpose.
		for (int i = 0; i < hold.length; i++) {
			for (int j = 0; j < hold[i].length; j++) {
				ICell cell = hold[i][j];
				if (cell instanceof ISlot) {
					ISlot slot = (ISlot) cell;
					if (slot.getId() >= 1 && slot.getId() <= 4 && slot.getContent() == null) {
						return slot;
					}
				}
			}
		}
		return null;
	}
	
	public void setSlotToOccupied(int slotId) {
		for (int i = 0 ; i < 7; i++) {
			for (int j = 0 ; j < 7; j++) {
				if (this.hold[i][j].getType() == CellType.SLOT) {
				    Slot slot = (Slot) this.hold[i][j];

				    if (slot.getId() == slotId) {
				        slot.setContent(new Container());
				    }
				}
			}
		}
	}
	
}
