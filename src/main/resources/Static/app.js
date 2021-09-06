var stompClient = null;

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);

}

function connect() {
    var socket = new SockJS('/mqttClientDashboard');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);
//        var header = {ack: 'card'};
        stompClient.subscribe('/topic/replies', function (message) {
            showReplies(JSON.parse(message.body));
             console.log('card: ' + message.card);
        });
        stompClient.subscribe('/topic/data', function (message) {
            showMessages(JSON.parse(message.body));
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
//    console.log('create command: ' + commandNumber);
}

function showReplies(message) {
//    console.log('message: ' + message.name);
//    console.log('card: ' + message);
//    document.getElementById(message.name + '-timestamp').innerHTML = message.timestamp;
//    if (message.result.toUpperCase() === 'OK') {
//        var image = new Image();
//        image.src = 'data:image/png;base64,' + message.payload;
//        document.getElementById(message.name + '-payload').innerHTML = '<img src="' + image.src + '" class="img-fluid" alt="...">';
//        var saveButton = document.getElementById(message.name + '-save');
//        saveButton.setAttribute('href', image.src);
//        saveButton.classList.remove("disabled");
//    }
}

function showMessages(message, card) {
//    console.log('message: ' + message.name);


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
    $("#sendCommand ").click(function (data) {
        var arg = $(data).attr('data-arg');
        createCommand('1');
    });


});



