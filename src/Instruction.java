import utils.AddressingModeFunction;
import utils.InstructionFunction;

public class Instruction {

    String name;
    InstructionFunction operate;
    AddressingModeFunction addrmode;
    public String addrmodeName;
    int cycles;
    
    
    public Instruction(String name, InstructionFunction operate,
            AddressingModeFunction addrmode,String addrmodeName, int cycles) {
        this.name = name;
        this.operate = operate;
        this.addrmode = addrmode;
        this.addrmodeName = addrmodeName;
        this.cycles = cycles;
    }

  
    
}
