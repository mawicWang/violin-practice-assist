
import abcjs from 'abcjs';

// Mock DOM
global.document = {
    getElementById: () => ({ innerHTML: '' }),
    createElement: () => ({ setAttribute: () => {}, style: {} }),
};
global.window = {
    devicePixelRatio: 1
};

const abc = "X:1\nT:Test\nK:C\nCDEFG";
// Pass a dummy element instead of ID string to avoid getElementById lookup if possible,
// but abcjs might still try.
// renderAbc returns an array of tune objects.
const visualObj = abcjs.renderAbc("paper", abc);
const midiBuffer = abcjs.synth.getMidiFile(visualObj[0], { midiOutputType: 'binary' });

console.log("Is Uint8Array:", midiBuffer instanceof Uint8Array);
// It might return a Blob or string if we are not careful?
// Documentation says: "If this is set to binary, then the actual contents of the midi file are returned, as a blob."
// Wait, "blob"? In browser environment. In node?
console.log("Type:", typeof midiBuffer);
console.log("Constructor:", midiBuffer.constructor.name);

if (midiBuffer instanceof Uint8Array) {
    console.log("Length:", midiBuffer.length);
    console.log("First 4 bytes:", midiBuffer.slice(0, 4));
    // MThd in hex: 4D 54 68 64
    if (midiBuffer[0] === 0x4D && midiBuffer[1] === 0x54 && midiBuffer[2] === 0x68 && midiBuffer[3] === 0x64) {
        console.log("Header is MThd");
    } else {
        console.log("Header is INVALID");
    }
} else {
    console.log("Not a Uint8Array. Value:", midiBuffer);
}
