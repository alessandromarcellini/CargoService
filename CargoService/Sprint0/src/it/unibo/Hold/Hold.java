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
		// TODO
		return new Slot(0);
	}
	
}
