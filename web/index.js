  "use strict";

function goButtonPressed() {
	var edit = document.getElementById("urlEdit");
	var uri = edit.value;
	var queryParam = "?imgproxyhost=";
	window.location.href = queryParam+uri;
}

function keyPressed(e) {
	window.wasKeyJustPressed = true;
	window.lastKeyPressed = e.keyCode - 1.0;
    if (e.keyCode == 13) {
    	goButtonPressed();
    }
}
  
function createShader(gl, type, source) {
  var shader = gl.createShader(type);
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  var success = gl.getShaderParameter(shader, gl.COMPILE_STATUS);
  if (success) {
    return shader;
  }
  console.log(gl.getShaderInfoLog(shader));
  gl.deleteShader(shader);
}

function createProgram(gl, vertexShader, fragmentShader) {
  var program = gl.createProgram();
  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  var success = gl.getProgramParameter(program, gl.LINK_STATUS);
  if (success) {
    return program;
  }
  console.log(gl.getProgramInfoLog(program));
  gl.deleteProgram(program);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function main() {
	window.lastKeyPressed = 0.0;
  var canvas = document.getElementById("canvas");
  var gl = canvas.getContext("webgl");
  if (!gl) {
    return;
  }
  
  var vertexShaderSource = document.getElementById("2d-vertex-shader").text;
  var fragmentShaderSource = document.getElementById("2d-fragment-shader").text;
  var vertexShader = createShader(gl, gl.VERTEX_SHADER, vertexShaderSource);
  var fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, fragmentShaderSource);
  var program = createProgram(gl, vertexShader, fragmentShader);

  // look up where the vertex data needs to go.
  var positionAttributeLocation = gl.getAttribLocation(program, "a_position");
  // Create a buffer and put three 2d clip space points in it
  var positionBuffer = gl.createBuffer();
  // Bind it to ARRAY_BUFFER (think of it as ARRAY_BUFFER = positionBuffer)
  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
  var positions = [
     1.0, -1.0,
    -1.0,  1.0,     
     1.0,  1.0,
    -1.0, -1.0,
    -1.0,  1.0,
     1.0, -1.0
  ];
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(positions), gl.STATIC_DRAW);

  var lastKeyTime = 0.0;
  var keyAccumulator = 0.0;
  function animate(ms) {
	  if (window.wasKeyJustPressed) {
		  keyAccumulator += 1.0;
		  lastKeyTime = ms;
		  window.wasKeyJustPressed = false;
	  }
	  var deltaKeyTime = ms - lastKeyTime;
	  keyAccumulator = keyAccumulator / 1.1;
	  
	  // Resize
	  gl.canvas.width  = window.innerWidth;
	  gl.canvas.height = window.innerHeight;

  	gl.viewport(0, 0, gl.canvas.width, gl.canvas.height);
	  // Clear the canvas
	  gl.clearColor(0, 0, 0, 0.0);
	//  gl.clear(gl.COLOR_BUFFER_BIT);
	  var time = new Date().getTime() - new Date();
	  gl.clear( gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT );
	  gl.useProgram(program);
	
	  // Uniforms
	  gl.uniform1f( gl.getUniformLocation( program, 'iGlobalTime' ), ms/1000.0 );
	  gl.uniform2f( gl.getUniformLocation( program, 'iResolution' ), gl.canvas.width, gl.canvas.height );
	  gl.uniform1f( gl.getUniformLocation( program, 'timeSinceKey' ), deltaKeyTime );
	  gl.uniform1f( gl.getUniformLocation( program, 'keyAccumulator' ), keyAccumulator );
	  
	  // Turn on the attribute
	  gl.enableVertexAttribArray(positionAttributeLocation);
	
	  // Bind the position buffer.
	  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
	
	  // Tell the attribute how to get data out of positionBuffer (ARRAY_BUFFER)
	  var size = 2;          // 2 components per iteration
	  var type = gl.FLOAT;   // the data is 32bit floats
	  var normalize = false; // don't normalize the data
	  var stride = 0;        // 0 = move forward size * sizeof(type) each iteration to get the next position
	  var offset = 0;        // start at the beginning of the buffer
	  gl.vertexAttribPointer(
	      positionAttributeLocation, size, type, normalize, stride, offset)
	
	  // draw
	  var primitiveType = gl.TRIANGLES;
	  var offset = 0;
	  var count = 6;
	  gl.drawArrays(primitiveType, offset, count);
	  // Recurse
	  window.requestAnimationFrame(animate);
  }
  animate(0);
}

main();
