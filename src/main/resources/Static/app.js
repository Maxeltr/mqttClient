var stompClient = null;

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);

}

function connect() {
    var socket = new SockJS('/gs-guide-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/replies', function (message) {
            showReplies(JSON.parse(message.body));
        });
    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    console.log("Disconnected");
}

function createCommand(commandNumber) {
    stompClient.send("/app/createCommand", {}, JSON.stringify({'commandNumber': commandNumber}));
    console.log('create command: ' + commandNumber);
}

function showReplies(message) {
    console.log('message: ' + message.name);
    document.getElementById(message.name + '-timestamp').innerHTML = message.timestamp;
    var image = new Image();
    image.src = 'data:image/png;base64,' + message.payload;
    document.getElementById(message.name + '-payload').innerHTML = '<img src="' + image.src + '" class="img-fluid" alt="...">';
    var saveButton = document.getElementById(message.name + '-save');
    saveButton.setAttribute('href', image.src);
    saveButton.classList.remove("disabled");

}



$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $("#connect").click(function () {
        connect();
    });
    $("#disconnect").click(function () {
        disconnect();
    });
    $("#takeScreenshot").click(function () {
        createCommand('command1');
    });


});



