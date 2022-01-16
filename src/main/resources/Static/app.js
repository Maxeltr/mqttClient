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
        stompClient.subscribe('/topic/replies', function (message) {
            showReplies(JSON.parse(message.body), message.headers.card);
        });
        stompClient.subscribe('/topic/data', function (message) {
            showMessages(JSON.parse(message.body), message.headers.card);
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
}

function showReplies(message, card) {
    console.log(card);
    document.getElementById(card + '-timestamp').innerHTML = message.timestamp;
    if (message.result.toUpperCase() === 'OK') {
        if (message.name.toUpperCase() === 'TAKESCREENSHOT') {
            var image = new Image();
            image.src = 'data:image/png;base64,' + message.payload;
            document.getElementById(card + '-payload').innerHTML = '<img src="' + image.src + '" class="img-fluid" alt="...">';
            var saveButton = document.getElementById(card + '-save');
            saveButton.setAttribute('href', image.src);
            saveButton.classList.remove("disabled");
        } else {
            document.getElementById(card + '-payload').innerHTML = '<p>' + message.result + '<br>' + message.payload + '</p>';
        }
    } else {
        document.getElementById(card + '-payload').innerHTML = '<p>' + message.result + '<br>' + message.payload + '</p>';
    }
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
    $("#sendCommand ").click(function () {
        var arg = $(this).val()
        createCommand(arg);
    });


});



