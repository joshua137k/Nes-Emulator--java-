import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.swing.*;
import utils.FLAGS6502;

public class App extends JPanel {
    private Bus nes = new Bus();
    private Map<Integer, String> mapAsm = new HashMap<>();

    public App() {
        setPreferredSize(new Dimension(680, 480));
        setFocusable(true);

        // OnUserCreate
        loadProgram();
        mapAsm = nes.cpu.disassemble(0x0000, 0xFFFF);
        nes.cpu.reset();

        // Key event handlers
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                        do {
                            nes.cpu.clock();
                        } while (!nes.cpu.complete());
                        break;
                    case KeyEvent.VK_R:
                        nes.cpu.reset();
                        break;
                    case KeyEvent.VK_I:
                        nes.cpu.irq();
                        break;
                    case KeyEvent.VK_N:
                        nes.cpu.nmi();
                        break;
                }
                repaint();
            }
        });
    }

    private void loadProgram() {
        // Load Program (assembled at https://www.masswerk.at/6502/assembler.html)
		/*
			*=$8000
			LDX #10
			STX $0000
			LDX #3
			STX $0001
			LDY $0000
			LDA #0
			CLC
			loop
			ADC $0001
			DEY
			BNE loop
			STA $0002
			NOP
			NOP
			NOP
		*/
        String program = "A2 0A 8E 00 00 A2 03 8E 01 00 AC 00 00 A9 00 18 6D 01 00 88 D0 FA 8D 02 00 EA EA EA";
        StringTokenizer tokenizer = new StringTokenizer(program);
        int offset = 0x8000;
        while (tokenizer.hasMoreTokens()) {
            int data =  Integer.parseInt(tokenizer.nextToken(), 16);
            nes.write(offset++,data);
        }

        nes.ram[0xFFFC] = 0x00;
        nes.ram[0xFFFD] = 0x80;
    }

    private String hex(int n, int d) {
        return String.format("%0" + d + "X", n);
    }

    private void drawRam(Graphics g, int x, int y, int nAddr, int nRows, int nColumns) {
        int nRamX = x, nRamY = y;
        g.setColor(Color.white);
        for (int row = 0; row < nRows; row++) {
            StringBuilder sOffset = new StringBuilder("$" + hex(nAddr, 4) + ":");
            for (int col = 0; col < nColumns; col++) {
                sOffset.append(" ").append(hex(nes.read(nAddr, true), 2));
                nAddr += 1;
            }
            g.drawString(sOffset.toString(), nRamX, nRamY);
            nRamY += 10;
        }
    }

    private void drawCpu(Graphics g, int x, int y) {
        g.setColor(Color.WHITE);
        g.drawString("STATUS:", x, y);
        g.setColor((nes.cpu.status & FLAGS6502.N.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("N", x + 64, y);
        g.setColor((nes.cpu.status & FLAGS6502.V.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("V", x + 80, y);
        g.setColor((nes.cpu.status & FLAGS6502.U.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("-", x + 96, y);
        g.setColor((nes.cpu.status & FLAGS6502.B.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("B", x + 112, y);
        g.setColor((nes.cpu.status & FLAGS6502.D.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("D", x + 128, y);
        g.setColor((nes.cpu.status & FLAGS6502.I.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("I", x + 144, y);
        g.setColor((nes.cpu.status & FLAGS6502.Z.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("Z", x + 160, y);
        g.setColor((nes.cpu.status & FLAGS6502.C.getValue()) != 0 ? Color.GREEN : Color.RED);
        g.drawString("C", x + 178, y);
        g.setColor(Color.WHITE);
        g.drawString("PC: $" + hex(nes.cpu.pc, 4), x, y + 10);
        g.drawString("A: $" + hex(nes.cpu.a, 2) + "  [" + nes.cpu.a + "]", x, y + 20);
        g.drawString("X: $" + hex(nes.cpu.x, 2) + "  [" + nes.cpu.x + "]", x, y + 30);
        g.drawString("Y: $" + hex(nes.cpu.y, 2) + "  [" + nes.cpu.y + "]", x, y + 40);
        g.drawString("Stack P: $" + hex(nes.cpu.stkp, 4), x, y + 50);
    }

    private void drawCode(Graphics g, int x, int y, int nLines) {
        Map.Entry<Integer, String> it_a = mapAsm.entrySet().stream()
                .filter(e -> e.getKey() == nes.cpu.pc)
                .findFirst()
                .orElse(null);
        int nLineY = (nLines >> 1) * 10 + y;
        if (it_a != null) {
            g.setColor(Color.CYAN);
            g.drawString(it_a.getValue(), x, nLineY);
            g.setColor(Color.WHITE);
            while (nLineY < (nLines * 10) + y) {
                nLineY += 10;
                if ((it_a = getNextEntry(it_a)) != null) {
                    g.drawString(it_a.getValue(), x, nLineY);
                }
            }
        }

        it_a = mapAsm.entrySet().stream()
                .filter(e -> e.getKey() == nes.cpu.pc)
                .findFirst()
                .orElse(null);
        nLineY = (nLines >> 1) * 10 + y;
        if (it_a != null) {
            while (nLineY > y) {
                nLineY -= 10;
                if ((it_a = getPreviousEntry(it_a)) != null) {
                    g.drawString(it_a.getValue(), x, nLineY);
                }
            }
        }
    }

    private Map.Entry<Integer, String> getNextEntry(Map.Entry<Integer, String> currentEntry) {
        boolean foundCurrent = false;
        for (Map.Entry<Integer, String> entry : mapAsm.entrySet()) {
            if (foundCurrent) {
                return entry;
            }
            if (entry.equals(currentEntry)) {
                foundCurrent = true;
            }
        }
        return null;
    }

    private Map.Entry<Integer, String> getPreviousEntry(Map.Entry<Integer, String> currentEntry) {
        Map.Entry<Integer, String> previousEntry = null;
        for (Map.Entry<Integer, String> entry : mapAsm.entrySet()) {
            if (entry.equals(currentEntry)) {
                return previousEntry;
            }
            previousEntry = entry;
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());

        drawRam(g, 2, 22, 0x0000, 16, 16);
        drawRam(g, 2, 202, 0x8000, 16, 16);
        drawCpu(g, 448, 20);
        drawCode(g, 448, 92, 26);

        g.setColor(Color.WHITE);
        g.drawString("SPACE = Step Instruction    R = RESET    I = IRQ    N = NMI", 10, 370);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("olc6502 Demonstration");
        App demo = new App();
        frame.add(demo);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}

